package com.example.edgedashanalytics.util.video.analysis;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.edgedashanalytics.event.result.AddResultEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;
import com.example.edgedashanalytics.util.video.VideoManager;

import org.greenrobot.eventbus.EventBus;

public class AnalysisWorker extends Worker {
    private static final String TAG = AnalysisWorker.class.getSimpleName();

    public static final String IN_KEY = "inPath";
    public static final String OUT_KEY = "outPath";

    public AnalysisWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String inPath = getInputData().getString(IN_KEY);
        String outPath = getInputData().getString(OUT_KEY);
        if (inPath == null || outPath == null) {
            Log.e(TAG, "Input or output not specified");
            return Result.failure();
        }

        Video video = VideoManager.getVideoFromPath(getApplicationContext(), inPath);
        if (video == null) {
            Log.e(TAG, String.format("Could not retrieve %s", inPath));
            return Result.failure();
        }

        Log.d(TAG, String.format("Selected %s for analysis, output to %s", video.toString(), outPath));

        VideoAnalysis videoAnalysis = new VideoAnalysis(video.getData(), inPath);
        videoAnalysis.analyse(getApplicationContext());

        com.example.edgedashanalytics.model.Result result =
                new com.example.edgedashanalytics.model.Result(outPath, FileManager.getFilenameFromPath(outPath));
        EventBus.getDefault().post(new AddResultEvent(result));
        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));

        return Result.success();
    }
}
