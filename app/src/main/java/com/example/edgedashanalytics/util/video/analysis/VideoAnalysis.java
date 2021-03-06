package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.util.TimeManager;
import com.example.edgedashanalytics.util.file.JsonManager;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;
import com.example.edgedashanalytics.util.video.FfmpegTools;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public abstract class VideoAnalysis {
    private static final String TAG = VideoAnalysis.class.getSimpleName();
    private static final boolean DEFAULT_VERBOSE = false;
    private static final String DEFAULT_STOP_DIVISOR = "0";

    final static int TF_THREAD_NUM = 4;
    final static int THREAD_NUM = 2;

    final boolean verbose;

    private final double stopDivisor;
    private static Long durationMillis = null;

    /**
     * Set up default parameters
     */
    VideoAnalysis(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        this.verbose = pref.getBoolean(context.getString(R.string.verbose_output_key), DEFAULT_VERBOSE);
        this.stopDivisor = Double.parseDouble(pref.getString(
                context.getString(R.string.early_stop_divisor_key), DEFAULT_STOP_DIVISOR));
    }

    abstract Frame processFrame(Bitmap bitmap, int frameIndex, float scaleFactor);

    abstract void setup(int width, int height);

    abstract float getScaleFactor(int width);

    public abstract void printParameters();

    public void analyse(String inPath, String outPath) {
        processVideo(inPath, outPath);
    }

    private void processVideo(String inPath, String outPath) {
        File videoFile = new File(inPath);
        String videoName = videoFile.getName();
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();

        try {
            retriever.setDataSource(videoFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(I_TAG, String.format("Failed to set data source for %s: %s\n  %s",
                    videoName, e.getClass().getSimpleName(), e.getMessage()));
            return;
        }

        String totalFramesString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT);

        if (totalFramesString == null) {
            Log.e(TAG, String.format("Could not retrieve metadata from %s", videoName));
            return;
        }
        int totalFrames = Integer.parseInt(totalFramesString);

        Instant startTime = Instant.now();
        long startPower = PowerMonitor.getTotalPowerConsumption();
        String startString = String.format("Starting analysis of %s, %s frames", videoName, totalFrames);
        Log.d(I_TAG, startString);

        final List<Frame> frames = Collections.synchronizedList(new ArrayList<>(totalFrames));

        ExecutorService frameExecutor = Executors.newFixedThreadPool(THREAD_NUM);
        ExecutorService loopExecutor = Executors.newSingleThreadExecutor();
        loopExecutor.submit(processFramesLoop(retriever, totalFrames, frames, frameExecutor));

        boolean complete = false;

        if (durationMillis == null) {
            if (stopDivisor <= 0) {
                // Ten minutes in milliseconds
                durationMillis = 600000L;
            } else {
                FfmpegTools.setDuration(inPath);
                durationMillis = (long) ((FfmpegTools.getDuration() * 1000) / stopDivisor);
            }
        }

        try {
            loopExecutor.shutdown();
            complete = loopExecutor.awaitTermination(durationMillis, TimeUnit.MILLISECONDS);

            if (stopDivisor < 0) {
                // Guarantee complete processing
                frameExecutor.shutdown();
                //noinspection ResultOfMethodCallIgnored
                frameExecutor.awaitTermination(durationMillis * 60, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Log.e(I_TAG, String.format("Interrupted analysis of %s:\n  %s", videoName, e.getMessage()));
        }

        if (!complete) {
            frameExecutor.shutdown();
            int completedFrames = frames.size();
            Log.w(I_TAG, String.format("Stopped processing early for %s at %s frames, %s remaining",
                    videoName, completedFrames, totalFrames - completedFrames));
        }

        synchronized (frames) {
            JsonManager.writeResultsToJson(outPath, frames);
        }

        frameExecutor.shutdown();

        String time = TimeManager.getDurationString(startTime);
        long powerConsumption = PowerMonitor.getPowerConsumption(startPower);

        String endString = String.format(Locale.ENGLISH, "Completed analysis of %s in %ss, %dnW consumed",
                videoName, time, powerConsumption);
        Log.d(I_TAG, endString);
        PowerMonitor.printSummary();
    }

    private Runnable processFramesLoop(MediaMetadataRetriever retriever, int totalFrames,
                                       List<Frame> frames, ExecutorService executor) {
        return () -> {
            String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            int videoWidth = Integer.parseInt(videoWidthString);
            int videoHeight = Integer.parseInt(videoHeightString);

            float scaleFactor = getScaleFactor(videoWidth);
            int scaledWidth = (int) (videoWidth / scaleFactor);
            int scaledHeight = (int) (videoHeight / scaleFactor);

            setup(scaledWidth, scaledHeight);

            // MediaMetadataRetriever is inconsistent, seems to only reliably with x264, may fail with other codecs
            for (int i = 0; i < totalFrames; i++) {
                final Bitmap bitmap = retriever.getFrameAtIndex(i);
                final int k = i;
                executor.execute(() -> frames.add(processFrame(
                        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), k, scaleFactor)
                ));
            }
        };
    }
}
