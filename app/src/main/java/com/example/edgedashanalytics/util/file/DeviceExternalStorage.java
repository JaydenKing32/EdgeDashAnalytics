package com.example.edgedashanalytics.util.file;

import android.os.Environment;

public class DeviceExternalStorage {
    public static boolean externalStorageIsWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public static boolean externalStorageIsReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }
}
