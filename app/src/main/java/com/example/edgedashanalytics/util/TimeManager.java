package com.example.edgedashanalytics.util;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.util.Log;

import androidx.collection.SimpleArrayMap;

import org.apache.commons.lang3.time.DurationFormatUtils;

import java.time.Duration;
import java.time.Instant;

public class TimeManager {
    private static final SimpleArrayMap<String, Instant> startTimes = new SimpleArrayMap<>();

    public static String getDurationString(Instant start) {
        return getDurationString(start, true);
    }

    public static String getDurationString(Instant start, boolean roundUp) {
        long duration = Duration.between(start, Instant.now()).toMillis();
        return formatDuration(duration, roundUp);
    }

    private static String getDurationString(Instant start, Instant end) {
        long duration = Duration.between(start, end).toMillis();
        return formatDuration(duration, true);
    }

    private static String formatDuration(long duration, boolean roundUp) {
        String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");

        if (roundUp && time.equals("00.000")) {
            // Time value is less than one millisecond, round up to one millisecond
            return "00.001";
        }
        return time;
    }

    public static void addStartTime(String filename) {
        startTimes.put(filename, Instant.now());
    }

    public static Instant getStartTime(String filename) {
        return startTimes.get(filename);
    }

    public static void printTurnaroundTime(String filename, Instant end) {
        printTurnaroundTime(filename, filename, end);
    }

    public static void printTurnaroundTime(String originalName, String newName, Instant end) {
        Instant start = startTimes.get(originalName);

        if (start == null) {
            Log.w(I_TAG, String.format("Could not calculate the turnaround time of %s", newName));
        } else {
            String time = getDurationString(start, end);
            Log.i(I_TAG, String.format("Turnaround time of %s: %ss", newName, time));
        }
    }
}
