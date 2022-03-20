package com.example.edgedashanalytics.util.video.analysis;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;

import org.apache.commons.lang3.ArrayUtils;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp;
import org.tensorflow.lite.support.metadata.MetadataExtractor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// https://github.com/tensorflow/examples/blob/master/lite/examples/object_detection/android/lib_interpreter/src/main/java/org/tensorflow/lite/examples/detection/tflite/TFLiteObjectDetectionAPIModel.java
public class OuterSupport extends VideoAnalysis<OuterFrame> {
    private static final String TAG = OuterSupport.class.getSimpleName();

    // Float model
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private boolean isModelQuantized;
    // Config values.
    private int inputSize;
    // Pre-allocated buffers.
    private final List<String> labels = new ArrayList<>();
    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    private int[] outputShape;

    private ByteBuffer imgData;
    private Interpreter interpreter;

    // private float scaleFactor;
    private static TensorImage image = null;
    private static ImageProcessor imageProcessor = null;
    private RectF cropRegion = null;
    private int maxDetections;

    public OuterSupport(Context context) {
        super(context);

        String defaultModel = context.getString(R.string.default_object_model_key);
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        String modelFilename = pref.getString(context.getString(R.string.object_model_key), defaultModel);
        String labelFilename = "labelmap.txt";

        try {
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(THREAD_NUM);
            // options.setUseXNNPACK(true);
            MappedByteBuffer modelFile = FileUtil.loadMappedFile(context, modelFilename);
            interpreter = new Interpreter(modelFile, options);

            int[] inputShape = interpreter.getInputTensor(0).shape();
            int numBytesPerChannel = inputShape[0];
            int inputWidth = inputShape[1];
            int inputHeight = inputShape[2];
            int channelNum = inputShape[3];

            inputSize = Math.min(inputWidth, inputHeight);
            outputShape = interpreter.getOutputTensor(0).shape();
            maxDetections = outputShape[1];

            isModelQuantized = numBytesPerChannel == 1;
            // scaleFactor = 1280.0f / inputSize;

            MetadataExtractor metadata = new MetadataExtractor(modelFile);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(metadata.getAssociatedFile(labelFilename),
                    Charset.defaultCharset()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    labels.add(line);
                }
            }

            // Pre-allocate buffers.
            imgData = ByteBuffer.allocateDirect(inputSize * inputSize * channelNum * numBytesPerChannel);
            imgData.order(ByteOrder.nativeOrder());
            intValues = new int[inputSize * inputSize];

            outputLocations = new float[1][maxDetections][4];
            outputClasses = new float[1][maxDetections];
            outputScores = new float[1][maxDetections];
            numDetections = new float[1];
        } catch (IOException e) {
            Log.w(TAG, String.format("Model failure:\n  %s", e.getMessage()));
        }
    }

    @Override
    public void printParameters() {

    }

    @Override
    void processFrame(Bitmap bitmap, int frameIndex) {
        List<Hazard> hazards = detectHazards(bitmap);

        frames.add(new OuterFrame(frameIndex, hazards));
        Log.v(TAG, String.format("%s", frameIndex));
    }

    private List<Hazard> detectHazards(Bitmap bitmap) {
        if (cropRegion == null) {
            cropRegion = initRectF(bitmap.getWidth(), bitmap.getHeight());
        }

        RectF rect = new RectF(
                cropRegion.left * bitmap.getWidth(),
                cropRegion.top * bitmap.getHeight(),
                cropRegion.right * bitmap.getWidth(),
                cropRegion.bottom * bitmap.getHeight()
        );
        Bitmap detectBitmap = Bitmap.createBitmap((int) rect.width(), (int) rect.height(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(detectBitmap);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);

        // TensorImage inputTensor = processInputImage(detectBitmap, inputSize, inputSize);
        // TensorBuffer outputTensor = TensorBuffer.createFixedSize(outputShape, DataType.FLOAT32);
        // interpreter.run(inputTensor.getBuffer(), outputTensor.getBuffer().rewind());
        // float[] output = outputTensor.getFloatArray();

        // Copy the input data into TensorFlow.
        outputLocations = new float[1][maxDetections][4];
        outputClasses = new float[1][maxDetections];
        outputScores = new float[1][maxDetections];
        numDetections = new float[1];

        TensorImage inputTensor = processInputImage(detectBitmap, inputSize, inputSize);
        Object[] inputArray = {inputTensor.getBuffer()};

        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        // Run the inference call.
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap);

        int numDetectionsOutput = Math.min(maxDetections, (int) numDetections[0]);
        final ArrayList<Hazard> hazards = new ArrayList<>(numDetectionsOutput);
        List<Float> positions = new ArrayList<>();

        float widthRatio = detectBitmap.getWidth() / (float) inputSize;
        float heightRatio = detectBitmap.getHeight() / (float) inputSize;

        for (int i = 0; i < numDetectionsOutput; ++i) {
            float left = outputLocations[0][i][1] * inputSize * widthRatio;
            float top = outputLocations[0][i][0] * inputSize * heightRatio;
            float right = outputLocations[0][i][3] * inputSize * widthRatio;
            float bottom = outputLocations[0][i][2] * inputSize * heightRatio;

            positions.add(left);
            positions.add(top);
            positions.add(right);
            positions.add(bottom);

            RectF detection = new RectF(left, top, right, bottom);
            Rect boundingBox = new Rect();
            detection.roundOut(boundingBox);

            hazards.add(new Hazard(labels.get((int) outputClasses[0][i]), outputScores[0][i], false, boundingBox));
        }

        Matrix matrix = new Matrix();
        float[] points = ArrayUtils.toPrimitive(positions.toArray(new Float[0]), 0f);

        matrix.postTranslate(rect.left, rect.top);
        matrix.mapPoints(points);

        for (int i = 0; i < hazards.size(); i++) {
            hazards.get(i).setBBox(points[i * 2], points[i * 2 + 1], points[i * 2 + 2], points[i * 2 + 3]);
        }

        return hazards;
    }

    private List<Hazard> detectHazardsOld(Bitmap bitmap) {
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        // bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, inputSize, inputSize);

        imgData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        // Copy the input data into TensorFlow.
        outputLocations = new float[1][maxDetections][4];
        outputClasses = new float[1][maxDetections];
        outputScores = new float[1][maxDetections];
        numDetections = new float[1];

        Object[] inputArray = {imgData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);

        // Run the inference call.
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap);

        // Show the best detections.
        // after scaling them back to the input size.
        // You need to use the number of detections from the output and not the NUM_DETECTONS variable
        // declared on top
        // because on some models, they don't always output the same total number of detections
        // For example, your model's NUM_DETECTIONS = 20, but sometimes it only outputs 16 predictions
        // If you don't use the output's numDetections, you'll get nonsensical data
        int numDetectionsOutput = Math.min(maxDetections, (int) numDetections[0]);

        final ArrayList<Hazard> hazards = new ArrayList<>(numDetectionsOutput);
        // final float upscale = inputSize * scaleFactor;
        final float upscale = inputSize;
        for (int i = 0; i < numDetectionsOutput; ++i) {
            final RectF detection = new RectF(
                    outputLocations[0][i][1] * upscale,
                    outputLocations[0][i][0] * upscale,
                    outputLocations[0][i][3] * upscale,
                    outputLocations[0][i][2] * upscale
            );
            Rect boundingBox = new Rect();
            detection.roundOut(boundingBox);

            hazards.add(new Hazard(labels.get((int) outputClasses[0][i]), outputScores[0][i], false, boundingBox));
        }

        return hazards;
    }

    /**
     * Prepare input image for detection
     */
    private TensorImage processInputImage(Bitmap bitmap, int inputWidth, int inputHeight) {
        if (image != null) {
            image.load(bitmap);
            return imageProcessor.process(image);
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = Math.min(height, width);

        imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeWithCropOrPadOp(size, size))
                .add(new ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
                .build();
        image = new TensorImage(DataType.UINT8);
        image.load(bitmap);
        return imageProcessor.process(image);
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
}


