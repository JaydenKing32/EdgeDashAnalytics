package com.example.edgedashanalytics.util.file;

import static org.apache.commons.io.FilenameUtils.getBaseName;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();

    private static final String VIDEO_EXTENSION = "mp4";
    private static final String RESULT_EXTENSION = "json";
    private static final String RAW_DIR_NAME = "raw";
    private static final String RESULTS_DIR_NAME = "results";
    private static final String NEARBY_DIR_NAME = ".nearby";

    private static final File MOVIE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final File DOWN_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private static final File RAW_DIR = new File(MOVIE_DIR, RAW_DIR_NAME);
    private static final File RESULTS_DIR = new File(MOVIE_DIR, RESULTS_DIR_NAME);
    private static final File NEARBY_DIR = new File(DOWN_DIR, NEARBY_DIR_NAME);

    private static final List<File> DIRS = Arrays.asList(RAW_DIR, RESULTS_DIR, NEARBY_DIR);

    public static String getRawDirPath() {
        return RAW_DIR.getAbsolutePath();
    }

    public static String getResultDirPath() {
        return RESULTS_DIR.getAbsolutePath();
    }

    public static String getNearbyDirPath() {
        return NEARBY_DIR.getAbsolutePath();
    }

    public static void initialiseDirectories() {
        for (File dir : DIRS) {
            makeDirectory(dir);
        }
    }

    private static File makeDirectory(File dirPath) {
        if (DeviceExternalStorage.externalStorageIsWritable()) {
            Log.v(TAG, "External storage is readable");
            try {
                if (!dirPath.exists()) {
                    if (dirPath.mkdirs()) {
                        Log.v(TAG, String.format("Created new directory: %s", dirPath));
                        return dirPath;
                    } else {
                        Log.e(TAG, String.format("Failed to create new directory: %s", dirPath));
                    }
                } else {
                    Log.v(TAG, String.format("Directory already exists: %s", dirPath));
                    return dirPath;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "makeDirectory error: \n%s");
            }
        } else {
            Log.e(TAG, "External storage is not readable");
        }
        return null;
    }

    public static boolean isMp4(String filename) {
        int extensionStartIndex = filename.lastIndexOf('.') + 1;
        return filename.regionMatches(true, extensionStartIndex, VIDEO_EXTENSION, 0, VIDEO_EXTENSION.length());
    }

    public static void cleanDirectories() {
        // TODO add preference to choose removing raw videos
        List<File> dirs = Arrays.asList(RESULTS_DIR, NEARBY_DIR);

        for (File dir : dirs) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                Log.e(TAG, String.format("Failed to delete %s", dir.getAbsolutePath()));
                Log.e(TAG, "cleanVideoDirectories error: \n%s");
            }
        }
    }

    public static String getFilenameFromPath(String filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }

    private static String getResultNameFromVideoName(String filename) {
        return String.format("%s.%s", getBaseName(filename), RESULT_EXTENSION);
    }

    public static String getVideoNameFromResultName(String filename) {
        return String.format("%s.%s", getBaseName(filename), VIDEO_EXTENSION);
    }

    public static String getResultPathFromVideoName(String filename) {
        return String.format("%s/%s", getResultDirPath(), getResultNameFromVideoName(filename));
    }

    public static File uriToFile(Uri sourceUri, String destPath, Context context) {
        try {
            File result = new File(destPath);
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            FileUtils.copyInputStreamToFile(inputStream, result);

            return result;
        } catch (IOException e) {
            Log.e(TAG, String.format("Could not open %s", sourceUri));
            return null;
        }
    }
}
