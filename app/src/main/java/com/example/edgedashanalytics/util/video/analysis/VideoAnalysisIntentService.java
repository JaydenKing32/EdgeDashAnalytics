package com.example.edgedashanalytics.util.video.analysis;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.edgedashanalytics.event.result.AddResultEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Result;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;

import org.greenrobot.eventbus.EventBus;

// TODO: Replace with either:
//  https://developer.android.com/reference/android/app/job/JobScheduler
//  https://developer.android.com/reference/androidx/work/WorkManager
public class VideoAnalysisIntentService extends IntentService {
    private static final String TAG = VideoAnalysisIntentService.class.getSimpleName();

    public static final String VIDEO_KEY = "video";
    public static final String OUTPUT_KEY = "outputPath";

    public VideoAnalysisIntentService() {
        super("VideoAnalysisIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.v(TAG, "onHandleIntent");

        if (intent == null) {
            Log.e(TAG, "Null intent");
            return;
        }
        Video video = intent.getParcelableExtra(VIDEO_KEY);
        String output = intent.getStringExtra(OUTPUT_KEY);

        if (video == null) {
            Log.e(TAG, "Video not specified");
            return;
        }
        if (output == null) {
            Log.e(TAG, "Output not specified");
            return;
        }
        Log.d(TAG, video.toString());
        Log.d(TAG, output);

        VideoAnalysis videoAnalysis = new VideoAnalysis(video.getData(), output);
        videoAnalysis.analyse(getApplicationContext());

        Result result = new Result(output, FileManager.getFilenameFromPath(output));
        EventBus.getDefault().post(new AddResultEvent(result));
        EventBus.getDefault().post(new RemoveEvent(video, Type.PROCESSING));
    }
}
