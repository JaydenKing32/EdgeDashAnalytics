package com.example.edgedashanalytics.util.file;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;
import static org.apache.commons.io.FilenameUtils.getBaseName;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.util.video.FfmpegTools;
import com.example.edgedashanalytics.util.video.analysis.Frame;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class FileManager {
    private static final String TAG = FileManager.class.getSimpleName();

    private static final String VIDEO_EXTENSION = "mp4";
    private static final String RESULT_EXTENSION = "json";
    private static final String RAW_DIR_NAME = "raw";
    private static final String RESULTS_DIR_NAME = "results";
    private static final String NEARBY_DIR_NAME = ".nearby";
    private static final String SEGMENT_DIR_NAME = "segment";
    private static final String SEGMENT_RES_DIR_NAME = String.format("%s-res", SEGMENT_DIR_NAME);
    private static final String LOG_DIR_NAME = "out";

    private static final File MOVIE_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
    private static final File DOWN_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    private static final File DOC_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
    private static final File RAW_DIR = new File(MOVIE_DIR, RAW_DIR_NAME);
    private static final File RESULTS_DIR = new File(MOVIE_DIR, RESULTS_DIR_NAME);
    private static final File NEARBY_DIR = new File(DOWN_DIR, NEARBY_DIR_NAME);
    private static final File SEGMENT_DIR = new File(MOVIE_DIR, SEGMENT_DIR_NAME);
    private static final File SEGMENT_RES_DIR = new File(MOVIE_DIR, SEGMENT_RES_DIR_NAME);
    private static final File LOG_DIR = new File(DOC_DIR, LOG_DIR_NAME);

    private static final List<File> DIRS = Arrays.asList(
            RAW_DIR, RESULTS_DIR, NEARBY_DIR, SEGMENT_DIR, SEGMENT_RES_DIR, LOG_DIR);

    public static String getRawDirPath() {
        return RAW_DIR.getAbsolutePath();
    }

    public static String getResultDirPath() {
        return RESULTS_DIR.getAbsolutePath();
    }

    public static String getNearbyDirPath() {
        return NEARBY_DIR.getAbsolutePath();
    }

    public static String getSegmentDirPath() {
        return SEGMENT_DIR.getAbsolutePath();
    }

    public static String getSegmentDirPath(String subDir) {
        return makeDirectory(SEGMENT_DIR, subDir).getAbsolutePath();
    }

    public static String getSegmentResDirPath() {
        return SEGMENT_RES_DIR.getAbsolutePath();
    }

    private static String getSegmentResDirPath(String subDir) {
        return makeDirectory(SEGMENT_RES_DIR, subDir).getAbsolutePath();
    }

    public static String getSegmentResSubDirPath(String videoName) {
        String baseVideoName = FfmpegTools.getBaseName(videoName);
        return makeDirectory(SEGMENT_RES_DIR, baseVideoName).getAbsolutePath();
    }

    public static String getLogDirPath() {
        return LOG_DIR.getAbsolutePath();
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
                Log.e(TAG, String.format("makeDirectory error: \n%s", e.getMessage()));
            }
        } else {
            Log.e(TAG, "External storage is not readable");
        }
        return null;
    }

    private static File makeDirectory(File dir, String subDirName) {
        return makeDirectory(new File(dir, subDirName));
    }

    public static boolean isMp4(String filename) {
        int extensionStartIndex = filename.lastIndexOf('.') + 1;
        return filename.regionMatches(true, extensionStartIndex, VIDEO_EXTENSION, 0, VIDEO_EXTENSION.length());
    }

    private static boolean isJson(String filename) {
        int extensionStartIndex = filename.lastIndexOf('.') + 1;
        return filename.regionMatches(true, extensionStartIndex, RESULT_EXTENSION, 0, RESULT_EXTENSION.length());
    }

    public static void cleanDirectories(Context context) {
        Log.v(TAG, "Cleaning directories");
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        List<File> dirs = pref.getBoolean(context.getString(R.string.remove_raw_key), false) ?
                DIRS.stream().filter(d -> !d.equals(LOG_DIR)).collect(Collectors.toList()) :
                DIRS.stream().filter(d -> !d.equals(LOG_DIR) && !d.equals(RAW_DIR)).collect(Collectors.toList());

        for (File dir : dirs) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                Log.e(TAG, String.format("Failed to delete %s", dir.getAbsolutePath()));
                Log.e(TAG, String.format("cleanVideoDirectories error: \n%s", e.getMessage()));
            }
        }
    }

    public static boolean clearLogs() {
        try {
            Log.v(TAG, "Clearing logs");
            FileUtils.deleteDirectory(LOG_DIR);
            return true;
        } catch (IOException e) {
            Log.e(TAG, String.format("Failed to clear logs: \n%s", e.getMessage()));
            return false;
        }
    }

    public static String getFilenameFromPath(String filePath) {
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }

    public static String getResultNameFromVideoName(String filename) {
        return String.format("%s.%s", getBaseName(filename), RESULT_EXTENSION);
    }

    public static String getVideoNameFromResultName(String filename) {
        return String.format("%s.%s", getBaseName(filename), VIDEO_EXTENSION);
    }

    public static String getResultPathFromVideoName(String filename) {
        return String.format("%s/%s", getResultDirPath(), getResultNameFromVideoName(filename));
    }

    public static String getResultPathOrSegmentResPathFromVideoName(String filename) {
        if (FfmpegTools.isSegment(filename)) {
            return String.format("%s/%s", FileManager.getSegmentResSubDirPath(filename),
                    getResultNameFromVideoName(filename));
        } else {
            return getResultPathFromVideoName(filename);
        }
    }

    public static List<Result> getResultsFromDir(String dirPath) {
        File dir = new File(dirPath);

        if (!dir.isDirectory()) {
            Log.e(TAG, String.format("%s is not a directory", dir.getAbsolutePath()));
            return null;
        }
        Log.v(TAG, String.format("Retrieving results from %s", dir.getAbsolutePath()));

        File[] resultFiles = dir.listFiles();
        if (resultFiles == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }

        List<String> resultPaths = Arrays.stream(resultFiles)
                .map(File::getAbsolutePath).filter(FileManager::isJson).collect(Collectors.toList());
        List<Result> results = new ArrayList<>();
        for (String resultPath : resultPaths) {
            results.add(new Result(resultPath));
        }
        return results;
    }

    private static List<String> getChildPaths(File dir) {
        File[] files = dir.listFiles();

        if (files == null) {
            Log.e(TAG, String.format("Could not access contents of %s", dir.getAbsolutePath()));
            return null;
        }
        return Arrays.stream(files).map(File::getAbsolutePath).sorted(String::compareTo).collect(Collectors.toList());
    }

    public static Result mergeResults(String parentName) {
        Instant start = Instant.now();
        String baseName = FilenameUtils.getBaseName(parentName);
        List<String> resultPaths = getChildPaths(new File(getSegmentResDirPath(baseName)));
        Log.v(TAG, String.format("Starting merge of results of %s", baseName));

        if (resultPaths == null) {
            return null;
        }
        String outPath = String.format("%s/%s", getResultDirPath(), parentName);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Type collectionType = new TypeToken<Collection<Frame>>() {
        }.getType();

        int offset = 0;
        List<Frame> allFrames = new ArrayList<>();

        try {
            for (String resultPath : resultPaths) {
                FileReader fileReader = new FileReader(resultPath);
                JsonReader jsonReader = new JsonReader(fileReader);
                List<Frame> frames = gson.fromJson(jsonReader, collectionType);

                for (Frame frame : frames) {
                    frame.frame += offset;
                }

                offset = frames.get(frames.size() - 1).frame + 1;
                allFrames.addAll(frames);

                // Shouldn't be necessary, all frames should sorted as resultPaths are sorted
                // frames.sort(Comparator.comparing(o -> o.frame));
                fileReader.close();
                jsonReader.close();
            }

            FileWriter writer = new FileWriter(outPath);
            gson.toJson(allFrames, writer);
            writer.flush();
            writer.close();

            long duration = Duration.between(start, Instant.now()).toMillis();
            String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
            Log.d(I_TAG, String.format("Merged results of %s in %ss", baseName, time));

            return new Result(outPath);
        } catch (IOException e) {
            Log.e(TAG, String.format("Results merge error: \n%s", e.getMessage()));
        }
        return null;
    }
}
