package com.example.edgedashanalytics.util.file;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();

    public static final String VIDEO_EXTENSION = "mp4";
    public static final String RAW_DIR_NAME = "raw";

    private static final File MOVIE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final File RAW_DIR = new File(MOVIE_DIR, RAW_DIR_NAME);

    private static final List<File> DIRS = Arrays.asList(RAW_DIR);

    public static String getRawDirPath() {
        return RAW_DIR.getAbsolutePath();
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

    private static File makeDirectory(File dir, String subDirName) {
        return makeDirectory(new File(dir, subDirName));
    }

    // https://stackoverflow.com/a/9293885/8031185
    public static void copy(File source, File dest) throws IOException {
        try (InputStream in = new FileInputStream(source)) {
            try (OutputStream out = new FileOutputStream(dest)) {
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static boolean isMp4(String filename) {
        int extensionStartIndex = filename.lastIndexOf('.') + 1;
        return filename.regionMatches(true, extensionStartIndex, VIDEO_EXTENSION, 0, VIDEO_EXTENSION.length());
    }

    public static String getFilenameFromPath(String filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
}
