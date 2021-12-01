package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.hardware.HardwareInfo;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.stream.Collectors;

// https://www.tensorflow.org/lite/models/object_detection/overview
// https://tfhub.dev/tensorflow/collections/lite/task-library/object-detector/1
// https://www.tensorflow.org/lite/performance/best_practices
// https://www.tensorflow.org/lite/guide/android
// https://www.tensorflow.org/lite/inference_with_metadata/task_library/object_detector
// https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tf2.md
public class VideoAnalysis {
    private static final String TAG = VideoAnalysis.class.getSimpleName();

    private final int maxDetections;
    private final float minScore;
    private final int threadNum;
    private final int bufferSize;
    private final List<Frame> frames = new ArrayList<>();
    private final int capacity = 5;

    private int scaleFactor = 1;
    private boolean verbose = false;

    public VideoAnalysis(Context context) {
        this.maxDetections = -1;
        this.minScore = 0.2f;
        this.threadNum = 4;

        // Check if phone has at least (roughly) 2GB of RAM
        HardwareInfo hwi = new HardwareInfo(context);
        if (hwi.totalRam < 2000000000L) {
            this.bufferSize = 5;
        } else {
            this.bufferSize = 50;
        }
    }

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Video analysis parameters:");
        paramMessage.add(String.format("bufferSize: %s", bufferSize));
        paramMessage.add(String.format("maxDetections: %s", maxDetections));
        paramMessage.add(String.format("minScore: %s", minScore));
        paramMessage.add(String.format("threadNum: %s", threadNum));

        Log.i(I_TAG, paramMessage.toString());
    }

    public void analyse(String inPath, String outPath, Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        if (pref.getBoolean(context.getString(R.string.verbose_output_key), verbose)) {
            verbose = true;
        }
        processVideo(inPath, outPath, context);
    }

    private void processVideo(String inPath, String outPath, Context context) {
        ObjectDetector detector;
        try {
            ObjectDetector.ObjectDetectorOptions objectDetectorOptions =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(maxDetections)
                            .setScoreThreshold(minScore)
                            .setNumThreads(threadNum)
                            .setLabelAllowList(Collections.singletonList("person"))
                            .build();

            String defaultModel = context.getString(R.string.default_model_key);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String modelFilename = pref.getString(context.getString(R.string.model_key), defaultModel);

            detector = ObjectDetector.createFromFileAndOptions(context, modelFilename, objectDetectorOptions);
        } catch (IOException e) {
            Log.w(TAG, String.format("Model failure:\n  %s", e.getMessage()));
            return;
        }

        File videoFile = new File(inPath);
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(videoFile.getAbsolutePath());

        String videoName = videoFile.getName();
        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);

        if (totalFramesString == null) {
            Log.e(TAG, String.format("Could not retrieve metadata from %s", videoName));
            return;
        }
        int totalFrames = Integer.parseInt(totalFramesString);

        Instant start = Instant.now();
        String startString = String.format("Starting analysis of %s", videoName);
        Log.d(I_TAG, startString);
        Log.d(TAG, String.format("Total frames of %s: %d", videoName, totalFrames));

        startFrameProcessing(detector, retriever, totalFrames);
        writeResultsToJson(outPath);

        long duration = Duration.between(start, Instant.now()).toMillis();
        String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");

        String endString = String.format(Locale.ENGLISH, "Completed analysis of %s in %ss", videoName, time);
        Log.d(I_TAG, endString);
        PowerMonitor.printSummary();
    }

    private void startFrameProcessing(ObjectDetector detector, MediaMetadataRetriever retriever, int totalFrames) {
        processFramesLoop(detector, retriever, totalFrames);
        // scaledFramesLoop(detector, retriever, totalFrames);
    }

    private void processFramesLoop(ObjectDetector detector, MediaMetadataRetriever retriever, int totalFrames) {
        // getFramesAtIndex is inconsistent, seems to only reliably with x264, may fail with other codecs
        // Using getFramesAtIndex on a full video requires too much memory, while extracting each frame separately
        // through getFrameAtIndex is too slow. Instead use a buffer, extracting groups of frames
        for (int i = 0; i < totalFrames; i += bufferSize) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            int frameBuffSize = Integer.min(bufferSize, totalFrames - i);
            List<Bitmap> frameBuffer = retriever.getFramesAtIndex(i, frameBuffSize);

            for (int k = 0; k < frameBuffSize; k++) {
                Bitmap bitmap = frameBuffer.get(k);
                int curFrame = i + k;

                if (bitmap == null) {
                    Log.w(TAG, String.format("Could not extract frame at index %d", curFrame));
                    continue;
                }

                processFrame(detector, bitmap, curFrame);
            }
        }
    }

    private void scaledFramesLoop(ObjectDetector detector, MediaMetadataRetriever retriever, int totalFrames) {
        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

        if (videoWidthString == null || videoHeightString == null) {
            Log.e(TAG, "Could not retrieve metadata");
            return;
        }
        scaleFactor = 3;

        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);
        int scaledWidth = videoWidth / scaleFactor;
        int scaledHeight = videoHeight / scaleFactor;

        for (int i = 0; i < totalFrames; i += bufferSize) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            int frameBuffSize = Integer.min(bufferSize, totalFrames - i);
            // 1080p bitmaps too memory intensive, need to scale down
            List<Bitmap> frameBuffer = retriever.getFramesAtIndex(i, frameBuffSize).stream()
                    .map(b -> Bitmap.createScaledBitmap(b, scaledWidth, scaledHeight, false))
                    .collect(Collectors.toList());

            for (int k = 0; k < frameBuffSize; k++) {
                Bitmap bitmap = frameBuffer.get(k);
                int curFrame = i + k;

                if (bitmap == null) {
                    Log.w(TAG, String.format("Could not extract frame at index %d", curFrame));
                    continue;
                }

                processFrame(detector, bitmap, curFrame);
            }
        }
    }


    private void processFrame(ObjectDetector detector, Bitmap bitmap, int frameIndex) {
        if (Thread.currentThread().isInterrupted()) {
            Log.e(TAG, String.format("Stopping at frame %d", frameIndex));
            return;
        }
        TensorImage image = TensorImage.fromBitmap(bitmap);
        List<Detection> detectionList = detector.detect(image);
        List<Person> people = new ArrayList<>(detectionList.size());
        boolean overCapacity = detectionList.size() > capacity;

        for (Detection detection : detectionList) {
            List<Category> categoryList = detection.getCategories();

            if (categoryList == null || categoryList.size() == 0) {
                continue;
            }
            Category category = categoryList.get(0);
            RectF bb = detection.getBoundingBox();
            Rect boundingBox = new Rect(
                    (int) bb.left * scaleFactor,
                    (int) bb.top * scaleFactor,
                    (int) bb.right * scaleFactor,
                    (int) bb.bottom * scaleFactor
            );

            people.add(new Person(category.getScore(), isClose(detection, detectionList), boundingBox));
        }
        frames.add(new Frame(frameIndex, people, overCapacity));

        if (verbose) {
            String resultHead = String.format(Locale.ENGLISH,
                    "Analysis completed for frame: %04d\nDetected objects: %02d\n",
                    frameIndex, detectionList.size());
            StringBuilder builder = new StringBuilder(resultHead);

            for (Detection detection : detectionList) {
                String resultBody = getDetectionString(detection);
                builder.append(resultBody);

                // if (isClose(detection, detectionList)) {
                //     System.out.println(frameIndex);
                // }
            }
            builder.append('\n');

            String resultMessage = builder.toString();
            Log.v(TAG, resultMessage);
        }
    }

    private String getDetectionString(Detection detection) {
        List<Category> categoryList = detection.getCategories();

        if (categoryList == null || categoryList.size() == 0) {
            String noCategoriesString = "No categories found";
            Log.e(TAG, noCategoriesString);
            return noCategoriesString;
        }

        Category category = categoryList.get(0);
        float score = category.getScore();
        Rect boundingBox = new Rect();
        detection.getBoundingBox().roundOut(boundingBox);

        StringJoiner result = new StringJoiner("\n  ");
        result.add(String.format("Category: %s", category.getLabel()));
        result.add(String.format(Locale.ENGLISH, "Confidence: %.2f", score));
        result.add(String.format("BBox: %s", boundingBox));

        return String.format("%s\n", result.toString());
    }

    private void writeResultsToJson(String jsonFilePath) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Writer writer = new FileWriter(jsonFilePath);
            gson.toJson(frames, writer);

            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.w(TAG, String.format("Failed to write results file:\n  %s", e.getMessage()));
        }
    }

    private boolean isClose(Detection object, List<Detection> others) {
        Rect boxA = new Rect();
        Rect boxB = new Rect();
        object.getBoundingBox().roundOut(boxA);

        int offset = boxA.height() / 2;
        expandRect(boxA, offset);

        for (Detection other : others) {
            other.getBoundingBox().roundOut(boxB);
            // expandRect(boxB, offset);

            if (!object.equals(other) && boxA.intersect(boxB)) {
                return true;
            }
        }
        return false;
    }

    private void expandRect(Rect rect, int expandBy) {
        rect.left -= expandBy;
        rect.top -= expandBy;
        rect.bottom += expandBy;
        rect.right += expandBy;
    }
}
