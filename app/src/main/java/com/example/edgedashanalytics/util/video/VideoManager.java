package com.example.edgedashanalytics.util.video;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Build;
import android.provider.MediaStore.Video.Media;
import android.util.Log;

import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.DeviceExternalStorage;
import com.example.edgedashanalytics.util.file.FileManager;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VideoManager {
    private final static String TAG = VideoManager.class.getSimpleName();
    private final static String MIME_TYPE = "video/mp4";

    private VideoManager() {
    }

    public static List<Video> getAllVideoFromExternalStorageFolder(Context context, File file) {
        Log.v(TAG, "getAllVideoFromExternalStorageFolder");
        String[] projection = {
                Media._ID,
                Media.DATA,
                Media.DISPLAY_NAME,
                Media.SIZE,
                Media.MIME_TYPE
        };
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            String selection = Media.DATA + " LIKE ? ";
            String[] selectArgs = new String[]{"%" + file.getAbsolutePath() + "%"};
            Log.d("VideoManager", file.getAbsolutePath());
            return getVideosFromExternalStorage(context, projection, selection, selectArgs, Media.DEFAULT_SORT_ORDER);
        }
        return new ArrayList<>();
    }

    static List<Video> getVideosFromDir(Context context, String dirPath) {
        File dir = new File(dirPath);
        return getVideosFromDir(context, dir);
    }

    private static List<Video> getVideosFromDir(Context context, File dir) {
        if (!dir.isDirectory()) {
            Log.e(TAG, String.format("%s is not a directory", dir.getAbsolutePath()));
            return null;
        }
        Log.v(TAG, String.format("Retrieving videos from %s", dir.getAbsolutePath()));

        File[] videoFiles = dir.listFiles();
        if (videoFiles == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }

        List<String> vidPaths = Arrays.stream(videoFiles)
                .map(File::getAbsolutePath).filter(FileManager::isMp4).collect(Collectors.toList());
        List<Video> videos = new ArrayList<>();
        for (String vidPath : vidPaths) {
            videos.add(getVideoFromPath(context, vidPath));
        }
        return videos;
    }

    private static Video getVideoFromFile(Context context, File file) {
        if (file == null) {
            Log.e(TAG, "Null file");
            return null;
        }
        String[] projection = {
                Media._ID,
                Media.DATA,
                Media.DISPLAY_NAME,
                Media.SIZE,
                Media.MIME_TYPE
        };
        String selection = Media.DATA + "=?";
        String[] selectionArgs = new String[]{file.getAbsolutePath()};
        Cursor videoCursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, Media.DEFAULT_SORT_ORDER);

        if (videoCursor == null || !videoCursor.moveToFirst()) {
            Log.d(TAG, "videoCursor is null");
            return null;
        }
        Video video = videoFromCursor(videoCursor);
        videoCursor.close();

        if (video == null) {
            Log.v(TAG, String.format("Video (%s) is null", file.getAbsolutePath()));
        }

        return video;
    }

    public static Video getVideoFromPath(Context context, String path) {
        Log.v(TAG, String.format("Retrieving video from %s", path));
        Video video = getVideoFromFile(context, new File(path));

        if (video != null) {
            return video;
        } else {
            ContentValues values = new ContentValues();
            values.put(Media.TITLE, FilenameUtils.getBaseName(path));
            values.put(Media.MIME_TYPE, MIME_TYPE);
            values.put(Media.DISPLAY_NAME, "player");
            values.put(Media.DESCRIPTION, "");
            values.put(Media.DATE_ADDED, System.currentTimeMillis());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(Media.DATE_TAKEN, System.currentTimeMillis());
            }
            values.put(Media.DATA, path);
            context.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, values);

            return getVideoFromFile(context, new File(path));
        }
    }

    public static List<Video> getAllVideosFromExternalStorage(Context context, String[] projection) {
        Log.v(TAG, "getAllVideosFromExternalStorage");
        if (DeviceExternalStorage.externalStorageIsReadable()) {
            return getVideosFromExternalStorage(context, projection, null, null, null);
        }
        return new ArrayList<>();
    }

    private static List<Video> getVideosFromExternalStorage(Context context, String[] projection, String selection,
                                                            String[] selectionArgs, String sortOrder) {
        Cursor videoCursor = context.getContentResolver().query(Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder);

        if (videoCursor != null) {
            List<Video> videos = new ArrayList<>(videoCursor.getCount());
            getVideosFromCursor(videoCursor, videos);
            videoCursor.close();
            Log.d(TAG, String.format("%d videos return", videos.size()));
            return videos;
        } else {
            return null;
        }
    }

    private static void getVideosFromCursor(Cursor videoCursor, List<Video> videos) {
        boolean cursorIsNotEmpty = videoCursor.moveToFirst();
        if (cursorIsNotEmpty) {
            do {
                Video video = videoFromCursor(videoCursor);
                if (video != null) {
                    videos.add(video);
                    Log.d(TAG, video.toString());
                } else {
                    Log.w(TAG, "Video is null");
                }
            } while (videoCursor.moveToNext());
        }
    }

    private static Video videoFromCursor(Cursor cursor) {
        Log.v(TAG, "videoFromCursor");
        Video video = null;
        try {
            int idIndex = cursor.getColumnIndex(Media._ID);
            int nameIndex = cursor.getColumnIndex(Media.DISPLAY_NAME);
            int dataIndex = cursor.getColumnIndex(Media.DATA);
            int sizeIndex = cursor.getColumnIndex(Media.SIZE);

            if (idIndex < 0 || nameIndex < 0 || dataIndex < 0 || sizeIndex < 0) {
                Log.w(TAG, "videoFromCursor error: index is below zero");
                return null;
            }

            String id = cursor.getString(idIndex);
            String name = cursor.getString(nameIndex);
            String data = cursor.getString(dataIndex);
            BigInteger size;

            try {
                String sizeString = cursor.getString(sizeIndex);
                size = new BigInteger(sizeString);
            } catch (NullPointerException e) {
                Log.w(TAG, "videoFromCursor error: Could not retrieve video file size, setting to zero");
                size = new BigInteger("0");
            }

            video = new Video(id, name, data, MIME_TYPE, size);
        } catch (Exception e) {
            Log.w(TAG, String.format("videoFromCursor error: \n%s", e.getMessage()));
        }
        return video;
    }
}
