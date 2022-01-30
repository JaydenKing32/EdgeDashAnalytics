package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;

import androidx.collection.SimpleArrayMap;

import org.apache.commons.lang3.ArrayUtils;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;


// https://www.tensorflow.org/lite/examples/pose_estimation/overview
// https://www.tensorflow.org/lite/tutorials/pose_classification
// https://github.com/tensorflow/examples/tree/master/lite/examples/pose_estimation/android
// https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/ml/MoveNet.kt
public class InnerAnalysis extends VideoAnalysis<InnerFrame> {
    private static final String TAG = InnerAnalysis.class.getSimpleName();

    private static final float MIN_SCORE = 0.2f;

    private Interpreter interpreter;
    private RectF cropRegion = null;
    private int inputWidth;
    private int inputHeight;
    private int[] outputShape;

    public InnerAnalysis(Context context) {
        super(context);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(threadNum);
        // String modelFilename = "lite-model_movenet_singlepose_lightning_tflite_float16_4.tflite";
        String modelFilename = "lite-model_movenet_singlepose_thunder_tflite_float16_4.tflite";

        try {
            interpreter = new Interpreter(FileUtil.loadMappedFile(context, modelFilename), options);

            inputWidth = interpreter.getInputTensor(0).shape()[1];
            inputHeight = interpreter.getInputTensor(0).shape()[2];
            outputShape = interpreter.getOutputTensor(0).shape();
        } catch (IOException e) {
            Log.w(TAG, String.format("Model failure:\n  %s", e.getMessage()));
        }
    }

    void processFrame(Bitmap bitmap, int frameIndex) {
        // String videoPath = "/storage/emulated/0/Movies/dmd/inn_01_body.mp4";

        // estimatePoses
        if (cropRegion == null) {
            cropRegion = initRectF(bitmap.getWidth(), bitmap.getHeight());
        }

        float totalScore = 0;
        int numKeyPoints = outputShape[2];

        RectF rect = new RectF(
                cropRegion.left * bitmap.getWidth(),
                cropRegion.top * bitmap.getHeight(),
                cropRegion.right * bitmap.getWidth(),
                cropRegion.bottom * bitmap.getHeight()
        );
        Bitmap detectBitmap = Bitmap.createBitmap((int) rect.width(), (int) rect.height(),
                Bitmap.Config.ARGB_8888);
        // Might just be for visualisation, may be unnecessary
        Canvas canvas = new Canvas(detectBitmap);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);

        TensorImage inputTensor = processInputImage(detectBitmap, inputWidth, inputHeight);
        TensorBuffer outputTensor = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32);
        float widthRatio = detectBitmap.getWidth() / (float) inputWidth;
        float heightRatio = detectBitmap.getHeight() / (float) inputHeight;

        interpreter.run(inputTensor.getBuffer(), outputTensor.getBuffer().rewind());
        float[] output = outputTensor.getFloatArray();
        List<Float> positions = new ArrayList<>();
        List<KeyPoint> keyPoints = new ArrayList<>();

        // Don't bother keeping results for keyPoints of lower body parts,
        //  lower body part indexes start at BodyPart.LOWER_INDEX
        for (int a = 0; a < numKeyPoints && a < BodyPart.LOWER_INDEX; a++) {
            float x = output[a * 3 + 1] * inputWidth * widthRatio;
            float y = output[a * 3] * inputHeight * heightRatio;

            positions.add(x);
            positions.add(y);

            float score = output[a * 3 + 2];
            keyPoints.add(new KeyPoint(BodyPart.AS_ARRAY[a], new PointF(x, y), score));
            totalScore += score;
        }

        // Adjust keypoint coordinates to align with original bitmap dimensions
        Matrix matrix = new Matrix();
        float[] points = ArrayUtils.toPrimitive(positions.toArray(new Float[0]), 0f);

        matrix.postTranslate(rect.left, rect.top);
        matrix.mapPoints(points);

        for (int i = 0; i < keyPoints.size(); i++) {
            keyPoints.get(i).coordinate = new PointF(points[i * 2], points[i * 2 + 1]);
        }

        boolean distracted = isDistracted(keyPoints, bitmap.getWidth(), bitmap.getHeight());
        frames.add(new InnerFrame(frameIndex, distracted, totalScore, keyPoints));

        // May improve performance, investigate later
        // cropRegion = determineRectF(keyPoints, bitmap.getWidth(), bitmap.getHeight());

        if (verbose) {
            String resultHead = String.format(Locale.ENGLISH,
                    "Analysis completed for frame: %04d\nKeyPoints:\n", frameIndex);
            StringBuilder builder = new StringBuilder(resultHead);

            for (KeyPoint keyPoint : keyPoints) {
                builder.append("  ");
                builder.append(keyPoint.toString());
                builder.append('\n');
            }
            builder.append('\n');

            String resultMessage = builder.toString();
            Log.v(TAG, resultMessage);
        }
    }

    /**
     * Prepare input image for detection
     */
    private TensorImage processInputImage(Bitmap bitmap, int inputWidth, int inputHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int size = Math.min(height, width);

        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(size, size))
                // Example code is backwards? TODO: check both ways
                // .add(new ResizeOp(inputWidth, inputHeight, ResizeOp.ResizeMethod.BILINEAR))
                .add(new ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        TensorImage tensorImage = new TensorImage(DataType.UINT8);
        tensorImage.load(bitmap);
        return imageProcessor.process(tensorImage);
    }

    /**
     * Defines the default crop region.
     * The function provides the initial crop region (pads the full image from both
     * sides to make it a square image) when the algorithm cannot reliably determine
     * the crop region from the previous frame.
     */
    private RectF initRectF(int imageWidth, int imageHeight) {
        float xMin;
        float yMin;
        float width;
        float height;

        if (imageWidth > imageHeight) {
            width = 1f;
            height = imageWidth / (float) imageHeight;
            xMin = 0f;
            yMin = (imageHeight / 2f - imageWidth / 2f) / imageHeight;
        } else {
            height = 1f;
            width = imageHeight / (float) imageWidth;
            yMin = 0f;
            xMin = (imageWidth / 2f - imageHeight / 2f) / imageWidth;
        }
        return new RectF(xMin, yMin, xMin + width, yMin + height);
    }

    /**
     * Looking down, looking back (not reversing), drinking or eating
     * Fairly basic, not very sophisticated
     */
    private boolean isDistracted(List<KeyPoint> keyPoints, int imageWidth, int imageHeight) {
        SimpleArrayMap<BodyPart, KeyPoint> keyDict = new SimpleArrayMap<>(keyPoints.size());

        for (KeyPoint keyPoint : keyPoints) {
            // May return keyPoint results for body parts that are not actually visible (e.g. arms in face view),
            //  to address this, drop keyPoints with a very low confidence score
            if (keyPoint.score >= MIN_SCORE) {
                keyDict.put(keyPoint.bodyPart, keyPoint);
            }
        }

        KeyPoint wristR = keyDict.get(BodyPart.RIGHT_WRIST);
        KeyPoint wristL = keyDict.get(BodyPart.LEFT_WRIST);

        if (wristR == null && wristL == null) {
            if (verbose) {
                Log.v(TAG, "Could not identify wrist key points");
            }
            return false;
        }
        return areHandsOccupied(wristR, imageHeight) || areHandsOccupied(wristL, imageHeight);

        // TODO: Try getting average eye height position, flag if exceeds bounds
    }

    private boolean areHandsOccupied(KeyPoint keyPoint, int imageHeight) {
        if (keyPoint == null) {
            return false;
        }
        if (!keyPoint.bodyPart.equals(BodyPart.LEFT_WRIST) && !keyPoint.bodyPart.equals(BodyPart.RIGHT_WRIST)) {
            Log.w(TAG, "Passed incorrect body part to areHandsOccupied");
            return false;
        }
        // Y coordinates are top-down, not bottom-up
        return keyPoint.coordinate.y < (imageHeight * 0.75);
    }

    public void printParameters() {
        StringJoiner paramMessage = new StringJoiner("\n  ");
        paramMessage.add("Video analysis parameters:");
        paramMessage.add(String.format("bufferSize: %s", bufferSize));
        paramMessage.add(String.format("threadNum: %s", threadNum));

        Log.i(I_TAG, paramMessage.toString());
    }
}
