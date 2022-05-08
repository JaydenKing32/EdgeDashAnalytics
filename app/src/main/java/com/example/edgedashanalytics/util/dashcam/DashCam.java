package com.example.edgedashanalytics.util.dashcam;

import static com.example.edgedashanalytics.page.main.MainActivity.I_TAG;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.SimpleArrayMap;
import androidx.preference.PreferenceManager;

import com.example.edgedashanalytics.R;
import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.TimeManager;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.hardware.PowerMonitor;
import com.example.edgedashanalytics.util.video.VideoManager;
import com.tonyodev.fetch2.Download;
import com.tonyodev.fetch2.EnqueueAction;
import com.tonyodev.fetch2.Error;
import com.tonyodev.fetch2.Fetch;
import com.tonyodev.fetch2.FetchConfiguration;
import com.tonyodev.fetch2.FetchListener;
import com.tonyodev.fetch2.NetworkType;
import com.tonyodev.fetch2.Priority;
import com.tonyodev.fetch2.Request;
import com.tonyodev.fetch2core.DownloadBlock;
import com.tonyodev.fetch2core.Downloader;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

// TODO: convert to singleton
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
    private static final SimpleArrayMap<String, Long> downloadPowers = new SimpleArrayMap<>();

    private static Fetch fetch = null;
    // bytes per second / 1000, aka MB/s
    public static long latestDownloadSpeed = 0;
    private static boolean dualDownload = false;

    public static final int concurrentDownloads = 2;
    private static final long updateInterval = 10000;
    private static final int retryAttempts = 5;

    // Two subsets of videos, each comprised of 400 segments, every video is exactly two seconds in length
    private static int testSubsetCount = 400;
    private static List<String> testVideos;

    public static void setup(Context context) {
        if (fetch == null) {
            FetchConfiguration fetchConfiguration = new FetchConfiguration.Builder(context)
                    .setDownloadConcurrentLimit(concurrentDownloads)
                    .setProgressReportingInterval(updateInterval)
                    .setAutoRetryMaxAttempts(retryAttempts)
                    .build();

            fetch = Fetch.Impl.getInstance(fetchConfiguration);
            clearDownloads();
        }
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        dualDownload = pref.getBoolean(context.getString(R.string.dual_download_key), dualDownload);
        testSubsetCount = Integer.parseInt(pref.getString(
                context.getString(R.string.test_video_count_key), String.valueOf(testSubsetCount)));

        testVideos = IntStream.rangeClosed(1, testSubsetCount)
                .mapToObj(i -> String.format(Locale.ENGLISH, "%04d", i))
                .flatMap(num -> Stream.of(String.format("inn_%s.mp4", num), String.format("out_%s.mp4", num)))
                .sorted(DashCam::testVideoComparator)
                .collect(Collectors.toList());
    }

    public static void clearDownloads() {
        fetch.cancelAll();
        fetch.removeAll();
    }

    public static int getTestVideoCount() {
        return testSubsetCount * 2;
    }

    public static void startDownloadAll(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Video> downloadCallback = (video) -> EventBus.getDefault().post(new AddEvent(video, Type.RAW));
        executor.submit(downloadAll(downloadCallback, context));
    }

    private static Runnable downloadAll(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            List<String> allFiles = getViofoFilenames();

            if (allFiles == null) {
                Log.e(I_TAG, "Dash cam file list is null");
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
            Log.e(I_TAG, "Could not connect to dash cam");
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
            Log.e(I_TAG, "Could not connect to dash cam");
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

    /**
     * Old method of downloading videos through {@link FileUtils#copyURLToFile}
     * TODO: replace usages with {@link DashCam#downloadVideo(String)}
     */
    private static void downloadVideo(String url, Consumer<Video> downloadCallback, Context context) {
        String filename = FileManager.getFilenameFromPath(url);
        String filePath = String.format("%s/%s", FileManager.getRawDirPath(), filename);
        Log.v(TAG, String.format("Started download: %s", filename));
        Instant start = Instant.now();

        try {
            FileUtils.copyURLToFile(new URL(url), new File(filePath));
        } catch (IOException e) {
            Log.w(TAG, String.format("Video download error, retrying: \n%s", e.getMessage()));
            return;
        }

        Video video = VideoManager.getVideoFromPath(context, filePath);
        if (video == null) {
            try {
                String errorMessage = String.format("Failed to download %s, retrying in 5s", filename);
                Log.w(TAG, errorMessage);
                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();

                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Log.e(TAG, String.format("Thread interrupted: \n%s", e.getMessage()));
            }
            return;
        }
        downloads.add(filename);

        String time = TimeManager.getDurationString(start);
        Log.i(I_TAG, String.format("Successfully downloaded %s in %ss", filename, time));
        downloadCallback.accept(video);
    }

    private static void downloadVideo(String url) {
        String filename = FileManager.getFilenameFromPath(url);
        String filePath = String.format("%s/%s", FileManager.getRawDirPath(), filename);
        downloads.add(filename);

        final Request request = new Request(url, filePath);
        request.setPriority(Priority.HIGH);
        request.setNetworkType(NetworkType.ALL);
        request.setEnqueueAction(EnqueueAction.REPLACE_EXISTING);
        fetch.enqueue(request,
                updatedRequest -> Log.d(I_TAG, String.format("Enqueued %s", FilenameUtils.getName(request.getFile()))),
                error -> Log.d(I_TAG, String.format("Enqueueing error: %s", error)));
    }

    public static Runnable downloadLatestVideos(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            Log.v(TAG, "Starting downloadLatestVideos");
            List<String> allVideos = getBlackvueFilenames();

            if (allVideos == null || allVideos.size() == 0) {
                Log.e(I_TAG, "Couldn't download videos");
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

    public static Runnable downloadTestVideos() {
        return () -> {
            List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(testVideos, downloads));
            newVideos.sort(DashCam::testVideoComparator);

            if (newVideos.size() != 0) {
                downloadVideo(videoDirUrl + newVideos.get(0));

                if (dualDownload && newVideos.size() > 1) {
                    downloadVideo(videoDirUrl + newVideos.get(1));
                }
            } else {
                Log.v(TAG, "All test videos queued for download");
            }
        };
    }

    private static boolean popTestDownload() {
        if (!testVideos.isEmpty()) {
            String filename = testVideos.remove(0);
            downloads.add(filename);
            downloadVideo(videoDirUrl + filename);

            return true;
        } else {
            return false;
        }
    }

    public static void downloadTestVideosLoop(Context context) {
        fetch.addListener(getFetchListener(context, v -> {
            if (v != null) {
                EventBus.getDefault().post(new AddEvent(v, Type.RAW));
            }
        }));

        Instant start = Instant.now();
        ScheduledExecutorService downloadExecutor = Executors.newSingleThreadScheduledExecutor();

        Runnable downloadRunnable = () -> {
            if (!popTestDownload()) {
                downloadExecutor.shutdown();
                String time = TimeManager.getDurationString(start);
                Log.i(TAG, String.format("All test videos scheduled for download in %ss", time));
            }
            if (dualDownload) {
                popTestDownload();
            }
        };

        String defaultDelay = "1000";
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(context);
        int delay = Integer.parseInt(pref.getString(context.getString(R.string.download_delay_key), defaultDelay));

        downloadExecutor.scheduleWithFixedDelay(downloadRunnable, 0, delay, TimeUnit.MILLISECONDS);
    }

    public static int testVideoComparator(String videoA, String videoB) {
        String prefixA = videoA.substring(0, 3);
        String prefixB = videoB.substring(0, 3);
        int suffixA = Integer.parseInt(StringUtils.substringBetween(videoA, "_", "."));
        int suffixB = Integer.parseInt(StringUtils.substringBetween(videoB, "_", "."));

        // Order sequentially based on suffix index
        if (suffixA != suffixB) {
            return suffixA - suffixB;
        }
        // Order "out" before "inn"
        return -prefixA.compareTo(prefixB);
    }

    public static void setDownloadCallback(Context context, Consumer<Video> downloadCallback) {
        fetch.addListener(getFetchListener(context, downloadCallback));
    }

    // public static Bitmap getLiveBitmap() {
    //     FFmpegMediaMetadataRetriever retriever = new FFmpegMediaMetadataRetriever();
    //     retriever.setDataSource("rtsp://192.168.1.254");
    //
    //     return retriever.getFrameAtTime();
    // }

    private static FetchListener getFetchListener(Context context, Consumer<Video> downloadCallback) {
        return new FetchListener() {
            public void onStarted(@NonNull Download d, @NonNull List<? extends DownloadBlock> list, int i) {
                String filename = FileManager.getFilenameFromPath(d.getFile());

                TimeManager.addStartTime(filename);
                downloadPowers.put(filename, PowerMonitor.getTotalPowerConsumption());
                Log.d(I_TAG, String.format("Started download: %s", filename));
            }

            @Override
            public void onCompleted(@NonNull Download d) {
                Video video = VideoManager.getVideoFromPath(context, d.getFile());
                String videoName = video.getName();

                String time;
                long power;

                Instant start = TimeManager.getStartTime(videoName);

                if (start != null) {
                    time = TimeManager.getDurationString(start);
                } else {
                    Log.e(I_TAG, String.format("Could not record download time of %s", videoName));
                    time = "0.000";
                }

                if (downloadPowers.containsKey(videoName)) {
                    power = PowerMonitor.getPowerConsumption(downloadPowers.remove(videoName));
                } else {
                    Log.e(TAG, String.format("Could not record download power consumption of %s", videoName));
                    power = 0;
                }

                Log.i(I_TAG, String.format("Successfully downloaded %s in %ss, %dnW consumed", videoName, time, power));
                downloadCallback.accept(video);
            }

            public void onProgress(@NonNull Download d, long etaMilli, long bytesPerSec) {
                latestDownloadSpeed = bytesPerSec / 1000;

                Log.v(TAG, String.format("Downloading %s, Progress: %3s%%, ETA: %2ss, MB/s: %4s",
                        FileManager.getFilenameFromPath(d.getUrl()),
                        d.getProgress(), etaMilli / 1000, latestDownloadSpeed
                ));
            }

            public void onAdded(@NonNull Download d) {
                // Stop condition for queuing test video downloads.
                // Won't work for non-test download scenarios, will need alternative stopping method.
                if (downloads.size() == testVideos.size()) {
                    Log.v(TAG, "All test videos queued for download, stopping queuing thread");
                    downloadCallback.accept(null);
                }
            }

            public void onError(@NonNull Download d, @NonNull Error error, @Nullable Throwable throwable) {
                Downloader.Response response = error.getHttpResponse();
                String responseString = response != null ? response.getErrorResponse() : "No response";

                Log.e(I_TAG, String.format("Error downloading %s (attempt %s):\n%s\n%s",
                        FileManager.getFilenameFromPath(d.getUrl()), d.getAutoRetryAttempts(), error, responseString));
            }

            // @formatter:off
            public void onQueued(@NonNull Download d, boolean b) {}
            public void onWaitingNetwork(@NonNull Download d) {}
            public void onDownloadBlockUpdated(@NonNull Download d, @NonNull DownloadBlock dBlock, int i) {}
            public void onPaused(@NonNull Download d) {}
            public void onResumed(@NonNull Download d) {}
            public void onCancelled(@NonNull Download d) {}
            public void onRemoved(@NonNull Download d) {}
            public void onDeleted(@NonNull Download d) {}
            // @formatter:on
        };
    }
}

