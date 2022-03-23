package com.example.edgedashanalytics.util.video;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;

import org.apache.commons.io.FilenameUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class FfmpegTools {
    private static final String TAG = FfmpegTools.class.getSimpleName();
    private static final char SEGMENT_SEPARATOR = '!';

    private static void executeFfmpeg(ArrayList<String> ffmpegArgs) {
        Log.i(TAG, String.format("Running ffmpeg with:\n  %s", TextUtils.join(" ", ffmpegArgs)));
        FFmpegKit.executeWithArguments(ffmpegArgs.toArray(new String[0]));
    }

    private static double getDuration(String filePath) {
        Log.v(TAG, String.format("Retrieving duration of %s", filePath));

        MediaInformationSession session = FFprobeKit.getMediaInformation(filePath);
        MediaInformation info = session.getMediaInformation();
        String durationString = info.getDuration();

        try {
            return Double.parseDouble(durationString);
        } catch (NumberFormatException e) {
            Log.e(I_TAG, String.format("ffmpeg-mobile error, could not retrieve duration of %s:\n%s",
                    filePath, e.getMessage()));
            return -1.0;
        }
    }

    // Get base video name from split segment's name
    public static String getBaseName(String segmentName) {
        String baseFileName = FilenameUtils.getBaseName(segmentName);
        String[] components = baseFileName.split(String.format("%c", SEGMENT_SEPARATOR));

        if (components.length > 0) {
            return components[0];
        } else {
            return baseFileName;
        }
    }

    public static int getSegmentCount(String segmentName) {
        String baseName = FilenameUtils.getBaseName(segmentName);
        String[] components = baseName.split(String.format("%c", SEGMENT_SEPARATOR));

        if (components.length < 1) {
            return -1;
        }

        String countString = components[components.length - 1];
        if (countString == null) {
            return -1;
        }

        return Integer.parseInt(countString);
    }

    public static boolean isSegment(String filename) {
        return filename.contains(String.format("%c", SEGMENT_SEPARATOR));
    }

    public static List<Video> splitAndReturn(Context context, String filePath, int segNum) {
        String baseName = FilenameUtils.getBaseName(filePath);

        if (segNum < 2) {
            Log.d(TAG, String.format("Segment number (%d) too low, returning whole video %s", segNum, baseName));
            return new ArrayList<>(Collections.singletonList(VideoManager.getVideoFromPath(context, filePath)));
        }

        splitVideo(filePath, segNum);
        String segmentDirPath = FileManager.getSegmentDirPath(baseName);
        List<Video> segments = VideoManager.getVideosFromDir(context, segmentDirPath);

        if (segments == null) {
            Log.e(I_TAG, String.format("Failed to split video, returning whole video %s", baseName));
            return new ArrayList<>(Collections.singletonList(VideoManager.getVideoFromPath(context, filePath)));
        }
        return VideoManager.getVideosFromDir(context, segmentDirPath);
    }

    // Segment filename format is "baseName!segIndex!segTotal.ext"
    private static void splitVideo(String filePath, int segNum) {
        if (segNum < 2) {
            return;
        }
        String baseName = FilenameUtils.getBaseName(filePath);

        // Round segment time up to ensure that the number of split videos doesn't exceed segNum
        int segTime = (int) Math.ceil(FfmpegTools.getDuration(filePath) / segNum);

        if (segTime < 2) {
            Log.w(TAG, String.format("Segment time (%ds) too low, aborting split", segTime));
            return;
        }

        String outDir = FileManager.getSegmentDirPath(baseName);
        Log.v(TAG, String.format("Splitting %s with %ds long segments", filePath, segTime));

        ArrayList<String> ffmpegArgs = new ArrayList<>(Arrays.asList(
                "-y",
                "-i", filePath,
                "-map", "0",
                "-c", "copy",
                "-f", "segment",
                "-reset_timestamps", "1",
                "-g", "0",
                "-segment_time", String.valueOf(segTime),
                String.format(Locale.ENGLISH, "%s/%s%c%%03d%c%d.%s",
                        outDir,
                        baseName,
                        SEGMENT_SEPARATOR,
                        SEGMENT_SEPARATOR,
                        segNum,
                        FilenameUtils.getExtension(filePath)
                )
        ));
        FfmpegTools.executeFfmpeg(ffmpegArgs);
    }
}
