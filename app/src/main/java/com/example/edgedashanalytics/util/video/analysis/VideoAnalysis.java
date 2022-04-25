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
    private static final String DEFAULT_SKIP = "0";
    private static final int SKIP_CHANGE = 10;
    private static final int SKIP_BREAKPOINT = 10;

    final static int TF_THREAD_NUM = 4;
    final static int THREAD_NUM = 2;

    final boolean verbose;
    private final int skipFrame;

    /**
     * Set up default parameters
     */
    VideoAnalysis(Context context) {
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        this.verbose = pref.getBoolean(context.getString(R.string.verbose_output_key), DEFAULT_VERBOSE);
        this.skipFrame = Integer.parseInt(pref.getString(context.getString(R.string.skip_frame_key), DEFAULT_SKIP));
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
        String startString = String.format("Starting analysis of %s", videoName);
        Log.d(I_TAG, startString);
        Log.d(TAG, String.format("Total frames of %s: %d", videoName, totalFrames));

        final List<Frame> frames = Collections.synchronizedList(new ArrayList<>(totalFrames));

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_NUM);
        processFramesLoop(retriever, totalFrames, frames, executor);
        executor.shutdown();
        boolean complete = false;

        try {
            complete = executor.awaitTermination(20, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Log.e(I_TAG, String.format("Interrupted analysis of %s:\n  %s", videoName, e.getMessage()));
        }

        if (!complete) {
            Log.e(I_TAG, "Could not complete processing in time");
            return;
        }
        JsonManager.writeResultsToJson(outPath, frames);

        String time = TimeManager.getDurationString(startTime);
        long powerConsumption = PowerMonitor.getPowerConsumption(startPower);

        String endString = String.format(Locale.ENGLISH, "Completed analysis of %s in %ss, %dnW consumed",
                videoName, time, powerConsumption);
        Log.d(I_TAG, endString);
        PowerMonitor.printSummary();
    }

    private void processFramesLoop(MediaMetadataRetriever retriever, int totalFrames,
                                   List<Frame> frames, ExecutorService executor) {
        String videoWidthString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String videoHeightString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        int videoWidth = Integer.parseInt(videoWidthString);
        int videoHeight = Integer.parseInt(videoHeightString);

        float scaleFactor = getScaleFactor(videoWidth);
        int scaledWidth = (int) (videoWidth / scaleFactor);
        int scaledHeight = (int) (videoHeight / scaleFactor);

        setup(scaledWidth, scaledHeight);

        int skipDiff = skipFrame - SKIP_CHANGE;
        boolean reachedBreakpoint = skipDiff >= SKIP_BREAKPOINT;
        int increment = reachedBreakpoint ? skipDiff : 1;

        // MediaMetadataRetriever is inconsistent, seems to only reliably with x264, may fail with other codecs
        for (int i = 0; i < totalFrames; i += increment) {
            final Bitmap bitmap = retriever.getFrameAtIndex(i);
            final int k = i;

            if (!reachedBreakpoint && shouldSkip(i, skipFrame)) {
                continue;
            }

            executor.submit(() -> frames.add(processFrame(
                    Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, false), k, scaleFactor)
            ));
        }
    }

    // If below 11, skip every nth, else skip all except every (n - 10)th
    private boolean shouldSkip(int i, int n) {
        if (n < 1) {
            return false;
        }
        if (n < SKIP_CHANGE + 1) {
            return i % n == 0;
        }
        return i % (n - SKIP_CHANGE) != 0;
    }
}
