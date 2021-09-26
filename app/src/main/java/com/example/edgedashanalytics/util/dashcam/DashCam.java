package com.example.edgedashanalytics.util.dashcam;

import android.content.Context;
import android.util.Log;

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
    private static final String baseUrl = "http://10.99.77.1/";
    private static final String videoDirUrl = baseUrl + "Record/";
    private static final Set<String> downloads = new HashSet<>();

    public static void startDownloadAll(Context context) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Consumer<Video> downloadCallback = (video) -> EventBus.getDefault().post(new AddEvent(video, Type.RAW));
        executor.submit(downloadAll(downloadCallback, context));
    }

    private static Runnable downloadAll(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            List<String> allFiles = getFilenames();
            int last_n = 2;

            if (allFiles == null) {
                Log.e(TAG, "Dashcam file list is null");
                return;
            }
            if (allFiles.size() < last_n) {
                Log.e(TAG, "Dashcam file list is smaller than expected");
                return;
            }
            List<String> lastFiles = allFiles.subList(Math.max(allFiles.size() - last_n, 0), allFiles.size());

            for (String filename : lastFiles) {
                String videoUrl = String.format("%s%s", videoDirUrl, filename);
                downloadVideo(videoUrl, downloadCallback, context);
            }
        };
    }

    private static List<String> getFilenames() {
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

    private static void downloadVideo(String url, Consumer<Video> downloadCallback, Context context) {
        String filename = FileManager.getFilenameFromPath(url);
        String filePath = String.format("%s/%s", FileManager.getRawDirPath(), filename);
        Log.v(TAG, String.format("Started downloading: %s", filename));
        Instant start = Instant.now();

        try {
            FileUtils.copyURLToFile(new URL(url), new File(filePath));
        } catch (IOException e) {
            Log.e(TAG, String.format("Download error: \n%s", e.getMessage()));
            return;
        }
        long duration = Duration.between(start, Instant.now()).toMillis();
        String time = DurationFormatUtils.formatDuration(duration, "ss.SSS");

        Video video = VideoManager.getVideoFromPath(context, filePath);
        Log.i(String.format("!%s", TAG), String.format("Successfully downloaded %s in %ss", filename, time));
        downloadCallback.accept(video);
    }

    public static Runnable downloadTestVideos(Consumer<Video> downloadCallback, Context context) {
        return () -> {
            List<String> newVideos = new ArrayList<>(CollectionUtils.disjunction(testVideos, downloads));
            newVideos.sort(Comparator.comparing(String::toString));

            if (newVideos.size() != 0) {
                // Get oldest new video, testVideos should already be sorted
                String toDownload = newVideos.get(0);
                downloads.add(toDownload);
                downloadVideo(videoDirUrl + toDownload, downloadCallback, context);
            } else {
                downloadCallback.accept(null);
            }
        };
    }

    private static final ArrayList<String> testVideos = new ArrayList<>(Arrays.asList(
            "b1c66a42-6f7d68ca.mp4",
            "b1c9c847-3bda4659.mp4",
            "b1ca2e5d-84cf9134.mp4",
            "b1cd1e94-26dd524f.mp4",
            "b1cd1e94-549d0bfe.mp4",
            "b1ceb32e-a106591d.mp4",
            "b1d0a191-03dcecc2.mp4",
            "b1d0a191-06deb55d.mp4",
            "b1d0a191-2ed2269e.mp4",
            "b1d0a191-65deaeef.mp4",
            "b1d0a191-de8948f6.mp4",
            "b1d10d08-c35503b8.mp4",
            "b1d22ed6-f1cac061.mp4",
            "b1d7b3ac-2a92e19f.mp4",
            "b1dac7f7-6b2e0382.mp4",
            "b1e0c01d-dd9e6e2f.mp4",
            "b1e1a7b8-0aec80e8.mp4",
            "b1e1a7b8-65ec7612.mp4",
            "b1ee702d-0ae1fc10.mp4",
            "b1f0efd9-e900c6e5.mp4",
            "b1f20aa0-3401c3bf.mp4",
            "b1f4491b-16256d7c.mp4",
            "b1f4491b-33824f31.mp4",
            "b1f4491b-9958bd99.mp4",
            "b1f62c41-ed0c6521.mp4",
            "b1ff4656-0435391e.mp4",
            "b2a8e8b4-50058f09.mp4",
            "b2a8e8b4-a4e93829.mp4",
            "b2a9d547-f7f6fa92.mp4",
            "b2ae4fc5-d1082ddf.mp4"
    ));

    /*
    private static final ArrayList<String> testVideos = new ArrayList<>(Arrays.asList(
            "S0=City_Center=Time_12-34=View_001.mp4",
            "S0=City_Center=Time_12-34=View_002.mp4",
            "S0=City_Center=Time_12-34=View_003.mp4",
            "S0=City_Center=Time_12-34=View_004.mp4",
            "S0=City_Center=Time_12-34=View_005.mp4",
            "S0=City_Center=Time_12-34=View_006.mp4",
            "S0=City_Center=Time_12-34=View_007.mp4",
            "S0=City_Center=Time_12-34=View_008.mp4",
            "S0=City_Center=Time_14-55=View_001.mp4",
            "S0=City_Center=Time_14-55=View_002.mp4",
            "S0=City_Center=Time_14-55=View_003.mp4",
            "S0=City_Center=Time_14-55=View_004.mp4",
            "S0=Regular_Flow=Time_13-57=View_001.mp4",
            "S0=Regular_Flow=Time_13-57=View_002.mp4",
            "S0=Regular_Flow=Time_13-57=View_003.mp4",
            "S0=Regular_Flow=Time_13-57=View_004.mp4",
            "S0=Regular_Flow=Time_13-59=View_001.mp4",
            "S0=Regular_Flow=Time_13-59=View_002.mp4",
            "S0=Regular_Flow=Time_13-59=View_003.mp4",
            "S0=Regular_Flow=Time_13-59=View_004.mp4",
            "S0=Regular_Flow=Time_14-03=View_001.mp4",
            "S0=Regular_Flow=Time_14-03=View_002.mp4",
            "S0=Regular_Flow=Time_14-03=View_003.mp4",
            "S0=Regular_Flow=Time_14-03=View_004.mp4",
            "S0=Regular_Flow=Time_14-06=View_001.mp4",
            "S0=Regular_Flow=Time_14-06=View_002.mp4",
            "S0=Regular_Flow=Time_14-06=View_003.mp4",
            "S0=Regular_Flow=Time_14-06=View_004.mp4",
            "S0=Regular_Flow=Time_14-29=View_001.mp4",
            "S0=Regular_Flow=Time_14-29=View_002.mp4",
            "S0=Regular_Flow=Time_14-29=View_003.mp4",
            "S0=Regular_Flow=Time_14-29=View_004.mp4"
    ));
     */
}

