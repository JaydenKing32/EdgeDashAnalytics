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

import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
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
public class OuterAnalysis extends VideoAnalysis<OuterFrame> {
    private static final String TAG = OuterAnalysis.class.getSimpleName();

    private final int maxDetections;
    private final float minScore;
    private int scaleFactor = 1;
    private ObjectDetector detector;

    public OuterAnalysis(Context context) {
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

        try {
            BaseOptions baseOptions = BaseOptions.builder().setNumThreads(threadNum).build();

            ObjectDetector.ObjectDetectorOptions objectDetectorOptions =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setMaxResults(maxDetections)
                            .setScoreThreshold(minScore)
                            .setLabelAllowList(Collections.singletonList("person"))
                            .build();

            String defaultModel = context.getString(R.string.default_model_key);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String modelFilename = pref.getString(context.getString(R.string.model_key), defaultModel);

            detector = ObjectDetector.createFromFileAndOptions(context, modelFilename, objectDetectorOptions);
        } catch (IOException e) {
            Log.w(TAG, String.format("Model failure:\n  %s", e.getMessage()));
        }
    }

    private void scaledFramesLoop(MediaMetadataRetriever retriever, int totalFrames) {
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

                processFrame(bitmap, curFrame);
            }
        }
    }

    void processFrame(Bitmap bitmap, int frameIndex) {
        if (Thread.currentThread().isInterrupted()) {
            Log.e(TAG, String.format("Stopping at frame %d", frameIndex));
            return;
        }
        TensorImage image = TensorImage.fromBitmap(bitmap);
        List<Detection> detectionList = detector.detect(image);
        List<Person> people = new ArrayList<>(detectionList.size());

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
        frames.add(new OuterFrame(frameIndex, people));

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

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Video analysis parameters:");
        paramMessage.add(String.format("bufferSize: %s", bufferSize));
        paramMessage.add(String.format("maxDetections: %s", maxDetections));
        paramMessage.add(String.format("minScore: %s", minScore));
        paramMessage.add(String.format("threadNum: %s", threadNum));

        Log.i(I_TAG, paramMessage.toString());
    }
}
