package com.example.edgedashanalytics.util.dashcam;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.video.VideoManager;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DashCam {
    private static final String TAG = DashCam.class.getSimpleName();
    // BlackVue
    // private static final String baseUrl = "http://10.99.77.1/";
    // private static final String videoDirUrl = baseUrl + "Record/";
    // VIOFO
    private static final String baseUrl = "http://192.168.1.254/DCIM/MOVIE/";
    private static final String videoDirUrl = baseUrl;
    // Video stream: rtsp://192.168.1.254
    private static final Set<String> downloads = new HashSet<>();

    public static void startDownloadAll(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Video> downloadCallback = (video) -> EventBus.getDefault().post(new AddEvent(video, Type.RAW));
        executor.submit(downloadAll(downloadCallback, context));
    }

    private static Runnable downloadAll(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            List<String> allFiles = getViofoFilenames();

            if (allFiles == null) {
                Log.e(TAG, "Dashcam file list is null");
                return;
            }

            for (String filename : allFiles) {
                String videoUrl = String.format("%s%s", videoDirUrl, filename);
                downloadVideo(videoUrl, downloadCallback, context);
            }
        };
    }

    private static List<String> getBlackvueFilenames() {
        Document doc;

        try {
            doc = Jsoup.connect(baseUrl + "blackvue_vod.cgi").get();
        } catch (IOException e) {
            Log.e(TAG, "Could not connect to dashcam");
            return null;
        }
        List<String> allFiles = new ArrayList<>();

        String raw = doc.select("body").text();
        Pattern pat = Pattern.compile(Pattern.quote("Record/") + "(.*?)" + Pattern.quote(",s:"));
        Matcher match = pat.matcher(raw);

        while (match.find()) {
            allFiles.add(match.group(1));
        }

        allFiles.sort(Comparator.comparing(String::toString));
        return allFiles;
    }

    private static List<String> getViofoFilenames() {
        Document doc;

        try {
            doc = Jsoup.connect(baseUrl).get();
        } catch (IOException e) {
            Log.e(TAG, "Could not connect to dashcam");
            return null;
        }
        List<String> allFiles = new ArrayList<>();

        String raw = doc.select("body").text();
        Pattern pat = Pattern.compile("(\\S+\\.MP4)");
        Matcher match = pat.matcher(raw);

        while (match.find()) {
            allFiles.add(match.group(1));
        }

        allFiles.sort(Comparator.comparing(String::toString));
        return allFiles;
    }

    private static void downloadVideo(String url, Consumer<Video> downloadCallback, Context context) {
        String filename = FileManager.getFilenameFromPath(url);
        String filePath = String.format("%s/%s", FileManager.getRawDirPath(), filename);
        Log.v(TAG, String.format("Started downloading: %s", filename));
        Instant start = Instant.now();

        try {
            FileUtils.copyURLToFile(new URL(url), new File(filePath));
        } catch (IOException e) {
            Log.e(TAG, String.format("Video download error, retrying: \n%s", e.getMessage()));
            return;
        }

        Video video = VideoManager.getVideoFromPath(context, filePath);
        if (video == null) {
            try {
                String errorMessage = String.format("Failed to download %s, retrying in 5s", filename);
                Log.e(TAG, errorMessage);
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();

                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Log.e(TAG, String.format("Thread interrupted: \n%s", e.getMessage()));
            }
            return;
        }
        downloads.add(filename);

        long duration = Duration.between(start, Instant.now()).toMillis();
        String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");
        Log.i(I_TAG, String.format("Successfully downloaded %s in %ss", filename, time));
        downloadCallback.accept(video);
    }

    public static Runnable downloadLatestVideos(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            Log.v(TAG, "Starting downloadLatestVideos");
            List<String> allVideos = getBlackvueFilenames();

            if (allVideos == null || allVideos.size() == 0) {
                Log.e(TAG, "Couldn't download videos");
                return;
            }
            List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(allVideos, downloads));
            newVideos.sort(Comparator.comparing(String::toString));

            if (newVideos.size() != 0) {
                // Get oldest new video
                String toDownload = newVideos.get(0);
                downloads.add(toDownload);

                downloadVideo(videoDirUrl + toDownload, downloadCallback, context);
            } else {
                Log.d(TAG, "No new videos");
            }
        };
    }

    public static Runnable downloadTestVideos(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(testVideos, downloads));
            newVideos.sort(DashCam::testVideoComparator);

            if (newVideos.size() != 0) {
                String toDownload = newVideos.get(0);
                Log.v(TAG, String.format("Passing to download callback: %s", toDownload));
                downloadVideo(videoDirUrl + toDownload, downloadCallback, context);
            } else {
                Log.v(TAG, "All test videos downloaded");
                downloadCallback.accept(null);
            }
        };
    }

    public static void downloadTestVideosLoop(Context c) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            for (String filename : testVideos) {
                downloadVideo(videoDirUrl + filename, v -> EventBus.getDefault().post(new AddEvent(v, Type.RAW)), c);
            }
        });
    }

    private static int testVideoComparator(String videoA, String videoB) {
        String prefixA = videoA.substring(0, 3);
        String prefixB = videoB.substring(0, 3);
        int suffixA = Integer.parseInt(videoA.substring(4, 6));
        int suffixB = Integer.parseInt(videoB.substring(4, 6));

        if (suffixA != suffixB) {
            return suffixA - suffixB;
        }
        return prefixA.compareTo(prefixB);
    }

    // public static Bitmap getLiveBitmap() {
    //     FFmpegMediaMetadataRetriever retriever = new FFmpegMediaMetadataRetriever();
    //     retriever.setDataSource("rtsp://192.168.1.254");
    //
    //     return retriever.getFrameAtTime();
    // }

    private static final ArrayList<String> testVideos = new ArrayList<>(Arrays.asList(
            "out_01.mp4", "inn_01.mp4",
            "out_02.mp4", "inn_02.mp4",
            "out_03.mp4", "inn_03.mp4",
            "out_04.mp4", "inn_04.mp4",
            "out_05.mp4", "inn_05.mp4",
            "out_06.mp4", "inn_06.mp4",
            "out_07.mp4", "inn_07.mp4",
            "out_08.mp4", "inn_08.mp4",
            "out_09.mp4", "inn_09.mp4",
            "out_10.mp4", "inn_10.mp4",
            "out_11.mp4", "inn_11.mp4",
            "out_12.mp4", "inn_12.mp4",
            "out_13.mp4", "inn_13.mp4",
            "out_14.mp4", "inn_14.mp4",
            "out_15.mp4", "inn_15.mp4",
            "out_16.mp4", "inn_16.mp4",
            "out_17.mp4", "inn_17.mp4",
            "out_18.mp4", "inn_18.mp4",
            "out_19.mp4", "inn_19.mp4",
            "out_20.mp4", "inn_20.mp4"
    ));
}

