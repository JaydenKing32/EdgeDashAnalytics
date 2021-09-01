package com.example.edgedashanalytics.util.video.analysis;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.util.JsonWriter;
import android.util.Log;

import org.apache.commons.lang3.time.DurationFormatUtils;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

// https://www.tensorflow.org/lite/models/object_detection/overview
// https://tfhub.dev/tensorflow/lite-model/ssd_mobilenet_v1/1/metadata/2
// https://www.tensorflow.org/lite/performance/best_practices
// https://www.tensorflow.org/lite/guide/android
// https://www.tensorflow.org/lite/inference_with_metadata/task_library/object_detector
// https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tf2.md
class VideoAnalysis {
    private static final String TAG = VideoAnalysis.class.getSimpleName();

    private final String inPath;
    private final String outPath;

    private final int maxDetections;
    private final float minScore;
    private final int threadNum;
    private final int bufferSize;

    private CountDownLatch completionLatch;
    private HashMap<Integer, List<Detection>> frameDetections;
    private long scaleFactor = 1;

    VideoAnalysis(String inPath, String outPath) {
        this.inPath = inPath;
        this.outPath = outPath;

        this.maxDetections = 10;
        this.minScore = 0.5f;
        this.threadNum = 4;
        this.bufferSize = 50;
    }

    // https://developer.android.com/guide/background/threading
    // https://developer.android.com/guide/components/processes-and-threads#WorkerThreads
    void analyse(Context context) {
        processVideo(context);
    }

    private void processVideo(Context context) {
        ObjectDetector detector;
        try {
            ObjectDetector.ObjectDetectorOptions objectDetectorOptions =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setMaxResults(maxDetections)
                            .setScoreThreshold(minScore)
                            .setNumThreads(threadNum)
                            .setLabelAllowList(Collections.singletonList("person"))
                            .build();

            // TODO: add preference to select model
            String modelFile = "lite-model_ssd_mobilenet_v1_1_metadata_2.tflite";
            // String modelFile = "lite-model_efficientdet_lite4_detection_metadata_2.tflite";
            detector = ObjectDetector.createFromFileAndOptions(context, modelFile, objectDetectorOptions);
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
        Log.d(String.format("!%s", TAG), startString);
        Log.d(TAG, String.format("Total frames of %s: %d", videoName, totalFrames));
        startFrameProcessing(detector, retriever, totalFrames);

        try {
            completionLatch.await();
            writeResultsToJson(outPath);

            long duration = Duration.between(start, Instant.now()).toMillis();
            String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");

            String endString = String.format(Locale.ENGLISH, "Completed analysis of %s in %ss with %d threads",
                    videoName, time, threadNum);
            Log.d(String.format("!%s", TAG), endString);
        } catch (InterruptedException e) {
            Log.w(TAG, String.format("Interrupted task:\n  %s", e.getMessage()));
        }
    }

    private void startFrameProcessing(ObjectDetector detector, MediaMetadataRetriever retriever,
                                      int totalFrames) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        completionLatch = new CountDownLatch(totalFrames);
        frameDetections = new HashMap<>(totalFrames);

        simpleFramesLoop(detector, retriever, totalFrames, executor);
//        delayedFramesLoop(detector, retriever, totalFrames, executor);
//        scaledFramesLoop(detector, retriever, totalFrames, executor);
    }

    private void simpleFramesLoop(ObjectDetector detector, MediaMetadataRetriever retriever,
                                  int totalFrames, ExecutorService executor) {
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

                Runnable imageRunnable = processFrame(detector, bitmap, curFrame);
                executor.submit(imageRunnable);
//                Runnable testRunnable = () -> Log.v(TAG, Integer.toString(curFrame));
//                executor.submit(testRunnable);
            }
        }
    }

    private void delayedFramesLoop(ObjectDetector detector, MediaMetadataRetriever retriever,
                                   int totalFrames, ExecutorService executor) {
        for (int i = 0; i < totalFrames; i += bufferSize) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            int frameBuffSize = Integer.min(bufferSize, totalFrames - i);
            List<Bitmap> frameBuffer = retriever.getFramesAtIndex(i, frameBuffSize);
            completionLatch = new CountDownLatch(frameBuffSize);

            for (int k = 0; k < frameBuffSize; k++) {
                Bitmap bitmap = frameBuffer.get(k);
                int curFrame = i + k;

                if (bitmap == null) {
                    Log.w(TAG, String.format("Could not extract frame at index %d", curFrame));
                    continue;
                }

                Runnable imageRunnable = processFrame(detector, bitmap, curFrame);
                executor.submit(imageRunnable);
            }
            try {
                completionLatch.await();
            } catch (InterruptedException e) {
                Log.w(TAG, String.format("Interrupted task:\n  %s", e.getMessage()));
            }
        }
    }

    private void scaledFramesLoop(ObjectDetector detector, MediaMetadataRetriever retriever,
                                  int totalFrames, ExecutorService executor) {
        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

        if (videoWidthString == null || videoHeightString == null) {
            Log.e(TAG, "Could not retrieve metadata");
            return;
        }
        scaleFactor = 3;

        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

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

                Runnable imageRunnable = processFrame(detector, bitmap, curFrame);
                executor.submit(imageRunnable);
            }
        }
    }


    private Runnable processFrame(ObjectDetector detector, Bitmap bitmap, int frameIndex) {
        return () -> {
            if (Thread.currentThread().isInterrupted()) {
                Log.e(TAG, String.format("Stopping at frame %d", frameIndex));
                return;
            }
            TensorImage image = TensorImage.fromBitmap(bitmap);
            List<Detection> detectionList = detector.detect(image);
            frameDetections.put(frameIndex, detectionList);

            String resultHead = String.format(Locale.ENGLISH,
                    "Analysis completed for frame: %04d\nDetected objects: %02d\n",
                    frameIndex, detectionList.size());
            StringBuilder builder = new StringBuilder(resultHead);

            for (Detection detection : detectionList) {
                String resultBody = getDetectionString(detection);
                builder.append(resultBody);

                if (isClose(detection, detectionList)) {
                    System.out.println(frameIndex);
                }
            }
            builder.append('\n');

            String resultMessage = builder.toString();
            Log.v(TAG, resultMessage);

//            Log.v(TAG, String.format("Latch count: %d", frameLatch.getCount()));
            completionLatch.countDown();
        };
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
        File jsonFile = new File(jsonFilePath);
        JsonWriter writer;
        try {
            FileOutputStream out = new FileOutputStream(jsonFile);
            writer = new JsonWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.setIndent(" ");
            writer.beginArray();

            for (Entry<Integer, List<Detection>> frameDetection : frameDetections.entrySet()) {
                writeFrame(writer, frameDetection);
            }

            writer.endArray();
            writer.close();
        } catch (Exception e) {
            Log.w(TAG, String.format("Interrupted task:\n  %s", e.getMessage()));
        }
    }

    private void writeFrame(JsonWriter writer, Entry<Integer, List<Detection>> frameDetection) throws IOException {
        int frameIndex = frameDetection.getKey();
        List<Detection> detectionList = frameDetection.getValue();

        writer.beginObject();
        writer.name("frame").value(frameIndex);
        writer.name("detections");

        writer.beginArray();
        for (Detection detection : detectionList) {
            boolean close = isClose(detection, detectionList);

            writeDetection(writer, detection, close);
        }
        writer.endArray();

        writer.endObject();
    }

    private void writeDetection(JsonWriter writer, Detection detection, boolean close) throws IOException {
        List<Category> categoryList = detection.getCategories();

        if (categoryList == null || categoryList.size() == 0) {
            Log.e(TAG, "No categories found");
            return;
        }
        Category category = categoryList.get(0);

        writer.beginObject();

        writer.name("category").value(category.getLabel());
        writer.name("confidence").value(category.getScore());
        writer.name("close").value(close);

        Rect boundingBox = new Rect();
        detection.getBoundingBox().roundOut(boundingBox);
        writer.name("BBox");
        writer.beginArray();
        writer.value(boundingBox.left * scaleFactor);
        writer.value(boundingBox.top * scaleFactor);
        writer.value(boundingBox.right * scaleFactor);
        writer.value(boundingBox.bottom * scaleFactor);
        writer.endArray();

        writer.endObject();
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
