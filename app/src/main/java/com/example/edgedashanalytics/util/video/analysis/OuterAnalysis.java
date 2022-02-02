package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;

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

// https://www.tensorflow.org/lite/models/object_detection/overview
// https://tfhub.dev/tensorflow/collections/lite/task-library/object-detector/1
// https://www.tensorflow.org/lite/performance/best_practices
// https://www.tensorflow.org/lite/guide/android
// https://www.tensorflow.org/lite/inference_with_metadata/task_library/object_detector
// https://github.com/tensorflow/models/blob/master/research/object_detection/g3doc/running_on_mobile_tf2.md
public class OuterAnalysis extends VideoAnalysis<OuterFrame> {
    private static final String TAG = OuterAnalysis.class.getSimpleName();

    // Different models have different maximum detection limits
    //  MobileNet's is 10, EfficientDet's is 25
    private static final int MAX_DETECTIONS = -1;
    private static final float MIN_SCORE = 0.2f;

    private ObjectDetector detector;

    // Include bicycle?
    private static final ArrayList<String> vehicleCategories = new ArrayList<>(Arrays.asList(
            "bicycle", "car", "motorcycle", "bus", "truck"
    ));

    public OuterAnalysis(Context context) {
        super(context);

        try {
            BaseOptions baseOptions = BaseOptions.builder().setNumThreads(THREAD_NUM).build();

            ObjectDetector.ObjectDetectorOptions objectDetectorOptions =
                    ObjectDetector.ObjectDetectorOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setMaxResults(MAX_DETECTIONS)
                            .setScoreThreshold(MIN_SCORE)
                            .build();

            String defaultModel = context.getString(R.string.default_object_model_key);
            SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
            String modelFilename = pref.getString(context.getString(R.string.object_model_key), defaultModel);

            detector = ObjectDetector.createFromFileAndOptions(context, modelFilename, objectDetectorOptions);
        } catch (IOException e) {
            Log.w(TAG, String.format("Model failure:\n  %s", e.getMessage()));
        }
    }

    void processFrame(Bitmap bitmap, int frameIndex) {
        TensorImage image = TensorImage.fromBitmap(bitmap);
        List<Detection> detectionList = detector.detect(image);
        List<Hazard> hazards = new ArrayList<>(detectionList.size());

        for (Detection detection : detectionList) {
            List<Category> categoryList = detection.getCategories();

            if (categoryList == null || categoryList.size() == 0) {
                continue;
            }
            Category category = categoryList.get(0);
            Rect boundingBox = new Rect();
            detection.getBoundingBox().roundOut(boundingBox);

            hazards.add(new Hazard(
                    category.getLabel(),
                    category.getScore(),
                    isDanger(detection, bitmap.getWidth(), bitmap.getHeight()),
                    boundingBox
            ));
        }
        frames.add(new OuterFrame(frameIndex, hazards));

        if (verbose) {
            String resultHead = String.format(
                    Locale.ENGLISH,
                    "Analysis completed for frame: %04d\nDetected hazards: %02d\n",
                    frameIndex, detectionList.size()
            );
            StringBuilder builder = new StringBuilder(resultHead);

            for (Detection detection : detectionList) {
                String resultBody = getDetectionString(detection);
                builder.append(resultBody);
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
        result.add(String.format(Locale.ENGLISH, "Score: %.2f", score));
        result.add(String.format("BBox: %s", boundingBox));

        return String.format("%s\n", result);
    }

    private Rect getDangerZone(int imageWidth, int imageHeight) {
        int dangerLeft = imageWidth / 4;
        int dangerRight = (imageWidth / 4) * 3;
        int dangerTop = imageHeight / 2;

        return new Rect(dangerLeft, dangerTop, dangerRight, imageHeight);
    }

    private boolean isDanger(Detection detection, int imageWidth, int imageHeight) {
        List<Category> categoryList = detection.getCategories();
        if (categoryList == null || categoryList.size() == 0) {
            return false;
        }

        String detCategory = categoryList.get(0).getLabel();
        Rect bb = new Rect();
        detection.getBoundingBox().roundOut(bb);

        if (vehicleCategories.contains(detCategory)) {
            // Close vehicles will have large bounding boxes
            return bb.height() > (imageHeight / 4) || bb.width() > (imageWidth / 4);
        } else {
            Rect dangerZone = getDangerZone(imageWidth, imageHeight);
            return dangerZone.contains(bb) || dangerZone.intersect(bb);
        }
    }

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Video analysis parameters:");
        paramMessage.add(String.format("bufferSize: %s", bufferSize));
        paramMessage.add(String.format("maxDetections: %s", MAX_DETECTIONS));
        paramMessage.add(String.format("minScore: %s", MIN_SCORE));
        paramMessage.add(String.format("threadNum: %s", THREAD_NUM));

        Log.i(I_TAG, paramMessage.toString());
    }
}
