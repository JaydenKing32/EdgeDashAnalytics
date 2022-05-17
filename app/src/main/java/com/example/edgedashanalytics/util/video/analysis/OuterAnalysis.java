package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.core.BaseOptions;
import org.tensorflow.lite.task.vision.detector.Detection;
import org.tensorflow.lite.task.vision.detector.ObjectDetector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// https://www.tensorflow.org/lite/models/object_detection/overview
// https://tfhub.dev/tensorflow/collections/lite/task-library/object-detector/1
// https://www.tensorflow.org/lite/performance/best_practices
// https://www.tensorflow.org/lite/guide/android
// https://www.tensorflow.org/lite/inference_with_metadata/task_library/object_detector
// https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tf2.md
public class OuterAnalysis extends VideoAnalysis {
    private static final String TAG = OuterAnalysis.class.getSimpleName();

    // Different models have different maximum detection limits
    //  MobileNet's is 10, EfficientDet's is 25
    private static final int MAX_DETECTIONS = -1;
    private static final float MIN_SCORE = 0.2f;

    private static int inputSize;

    private static final BlockingQueue<ObjectDetector> detectorQueue = new LinkedBlockingQueue<>(THREAD_NUM);

    // Include or exclude bicycles?
    private static final ArrayList<String> vehicleCategories = new ArrayList<>(Arrays.asList(
            "bicycle", "car", "motorcycle", "bus", "truck"
    ));

    public OuterAnalysis(Context context) {
        super(context);

        if (!detectorQueue.isEmpty()) {
            return;
        }

        String defaultModel = context.getString(R.string.default_object_model_key);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String modelFilename = pref.getString(context.getString(R.string.object_model_key), defaultModel);

        BaseOptions baseOptions;
        if (modelFilename.equals(defaultModel)) {
            baseOptions = BaseOptions.builder().setNumThreads(TF_THREAD_NUM).useNnapi().build();
        } else {
            baseOptions = BaseOptions.builder().setNumThreads(TF_THREAD_NUM).build();
        }

        ObjectDetector.ObjectDetectorOptions objectDetectorOptions = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMaxResults(MAX_DETECTIONS)
                .setScoreThreshold(MIN_SCORE)
                .build();

        try {
            Interpreter interpreter = new Interpreter(FileUtil.loadMappedFile(context, modelFilename));
            inputSize = interpreter.getInputTensor(0).shape()[1];
        } catch (IOException e) {
            Log.w(I_TAG, String.format("Model failure:\n  %s", e.getMessage()));
        }

        for (int i = 0; i < THREAD_NUM; i++) {
            try {
                detectorQueue.add(ObjectDetector.createFromFileAndOptions(
                        context, modelFilename, objectDetectorOptions));
            } catch (IOException e) {
                Log.w(I_TAG, String.format("Model failure:\n  %s", e.getMessage()));
            }
        }
    }

    OuterFrame processFrame(Bitmap bitmap, int frameIndex, float scaleFactor) {
        ObjectDetector detector;

        try {
            detector = detectorQueue.poll(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.w(I_TAG, String.format("Cannot acquire detector for frame %s:\n  %s", frameIndex, e.getMessage()));
            return null;
        }

        if (detector == null) {
            Log.w(I_TAG, String.format("Detector for frame %s is null", frameIndex));
            return null;
        }

        List<Detection> detectionList = detector.detect(TensorImage.fromBitmap(bitmap));

        try {
            detectorQueue.put(detector);
        } catch (InterruptedException e) {
            Log.w(TAG, String.format("Unable to return detector to queue:\n  %s", e.getMessage()));
        }

        List<Hazard> hazards = new ArrayList<>(detectionList.size());

        for (Detection detection : detectionList) {
            List<Category> categoryList = detection.getCategories();

            if (categoryList == null || categoryList.size() == 0) {
                continue;
            }
            Category category = categoryList.get(0);

            RectF detBox = detection.getBoundingBox();
            Rect boundingBox = new Rect(
                    (int) (detBox.left * scaleFactor),
                    (int) (detBox.top * scaleFactor),
                    (int) (detBox.right * scaleFactor),
                    (int) (detBox.bottom * scaleFactor)
            );

            int origWidth = (int) (bitmap.getWidth() * scaleFactor);
            int origHeight = (int) (bitmap.getHeight() * scaleFactor);

            hazards.add(new Hazard(
                    category.getLabel(),
                    category.getScore(),
                    isDanger(boundingBox, category.getLabel(), origWidth, origHeight),
                    boundingBox
            ));
        }

        if (verbose) {
            String resultHead = String.format(
                    Locale.ENGLISH,
                    "Analysis completed for frame: %04d\nDetected hazards: %02d\n",
                    frameIndex, hazards.size()
            );
            StringBuilder builder = new StringBuilder(resultHead);

            for (Hazard hazard : hazards) {
                builder.append("  ");
                builder.append(hazard.toString());
            }
            builder.append('\n');

            String resultMessage = builder.toString();
            Log.v(TAG, resultMessage);
        }

        return new OuterFrame(frameIndex, hazards);
    }

    private boolean isDanger(Rect boundingBox, String category, int imageWidth, int imageHeight) {
        if (vehicleCategories.contains(category)) {
            // Check tailgating
            Rect tailgateZone = getTailgateZone(imageWidth, imageHeight);
            return tailgateZone.contains(boundingBox) || tailgateZone.intersect(boundingBox);
        } else {
            // Check obstruction
            Rect dangerZone = getDangerZone(imageWidth, imageHeight);
            return dangerZone.contains(boundingBox) || dangerZone.intersect(boundingBox);
        }
    }

    private Rect getDangerZone(int imageWidth, int imageHeight) {
        int dangerLeft = imageWidth / 4;
        int dangerRight = (imageWidth / 4) * 3;
        int dangerTop = (imageHeight / 10) * 4;

        return new Rect(dangerLeft, dangerTop, dangerRight, imageHeight);
    }

    private Rect getTailgateZone(int imageWidth, int imageHeight) {
        int tailLeft = imageWidth / 3;
        int tailRight = (imageWidth / 3) * 2;
        int tailTop = (imageHeight / 4) * 3;
        // Exclude driving car's bonnet, assuming it always occupies the same space
        //  Realistically, due to dash cam position/angle, bonnets will occupy differing proportion of the video
        int tailBottom = imageHeight - imageHeight / 10;

        return new Rect(tailLeft, tailTop, tailRight, tailBottom);
    }

    void setup(int width, int height) {
        // Doesn't need setup
    }

    float getScaleFactor(int width) {
        return width / (float) inputSize;
    }

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Outer analysis parameters:");
        paramMessage.add(String.format("MAX_DETECTIONS: %s", MAX_DETECTIONS));
        paramMessage.add(String.format("MIN_SCORE: %s", MIN_SCORE));
        paramMessage.add(String.format("TensorFlow Threads: %s", TF_THREAD_NUM));
        paramMessage.add(String.format("Analysis Threads: %s", THREAD_NUM));

        Log.i(I_TAG, paramMessage.toString());
    }
}
