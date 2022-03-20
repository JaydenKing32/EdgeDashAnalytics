package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.hardware.HardwareInfo;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public abstract class VideoAnalysis<T extends Frame> {
    private static final String TAG = VideoAnalysis.class.getSimpleName();
    private static final boolean DEFAULT_VERBOSE = false;
    final static int THREAD_NUM = 4;

    final int bufferSize;
    final boolean verbose;

    /**
     * Set up default parameters
     */
    VideoAnalysis(Context context) {
        // Check if phone has at least (roughly) 2GB of RAM
        HardwareInfo hwi = new HardwareInfo(context);
        this.bufferSize = hwi.totalRam < 2000000000L ? 5 : 60;

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        this.verbose = pref.getBoolean(context.getString(R.string.verbose_output_key), DEFAULT_VERBOSE);
    }

    abstract void processFrame(List<T> frames, Bitmap bitmap, int frameIndex, float scaleFactor);

    abstract void setup(int width, int height);

    abstract float getScaleFactor(int width);

    public abstract void printParameters();

    public void analyse(String inPath, String outPath) {
        processVideo(inPath, outPath);
    }

    private void processVideo(String inPath, String outPath) {
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

        Instant startTime = Instant.now();
        long startPower = PowerMonitor.getTotalPowerConsumption();
        String startString = String.format("Starting analysis of %s", videoName);
        Log.d(I_TAG, startString);
        Log.d(TAG, String.format("Total frames of %s: %d", videoName, totalFrames));

        final List<T> frames = new ArrayList<>(totalFrames);

        processFramesLoop(retriever, totalFrames, frames);
        writeResultsToJson(outPath, frames);

        String time = FileManager.getDurationString(startTime);
        long powerConsumption = PowerMonitor.getPowerConsumption(startPower);

        String endString = String.format(Locale.ENGLISH, "Completed analysis of %s in %ss, %dnW consumed",
                videoName, time, powerConsumption);
        Log.d(I_TAG, endString);
        PowerMonitor.printSummary();
    }

    private void processFramesLoop(MediaMetadataRetriever retriever, int totalFrames, List<T> frames) {
        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        // float scaleFactor = 2f;
        float scaleFactor = getScaleFactor(videoWidth);
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        setup(scaledWidth, scaledHeight);

        Bitmap bitmap;
        List<Bitmap> frameBuffer;

        // getFramesAtIndex is inconsistent, seems to only reliably with x264, may fail with other codecs
        // Using getFramesAtIndex on a full video requires too much memory, while extracting each frame separately
        // through getFrameAtIndex is too slow. Instead use a buffer, extracting groups of frames
        for (int i = 0; i < totalFrames; i += bufferSize) {
            int frameBuffSize = Integer.min(bufferSize, totalFrames - i);
            // frameBuffer = retriever.getFramesAtIndex(i, frameBuffSize);
            frameBuffer = retriever.getFramesAtIndex(i, frameBuffSize).stream()
                    .map(b -> Bitmap.createScaledBitmap(b, scaledWidth, scaledHeight, false))
                    .collect(Collectors.toList());

            for (int k = 0; k < frameBuffSize; k++) {
                bitmap = frameBuffer.get(k);
                int curFrame = i + k;

                if (bitmap == null) {
                    Log.w(TAG, String.format("Could not extract frame at index %d", curFrame));
                    continue;
                }

                processFrame(frames, bitmap, curFrame, scaleFactor);
            }
        }
    }

    private void writeResultsToJson(String jsonFilePath, List<T> frames) {
        try {
            // Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Gson gson = new Gson();
            Writer writer = new FileWriter(jsonFilePath);
            gson.toJson(frames, writer);

            writer.flush();
            writer.close();
        } catch (Exception e) {
            Log.w(TAG, String.format("Failed to write results file:\n  %s", e.getMessage()));
        }
    }
}
