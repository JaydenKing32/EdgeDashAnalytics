package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.util.file.FileManager.getResultDirPath;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;
import com.example.edgedashanalytics.event.video.Type;
import com.example.edgedashanalytics.model.Video;
import com.example.edgedashanalytics.util.file.FileManager;

import org.greenrobot.eventbus.EventBus;

public class AnalysisTools {
    private final static String TAG = AnalysisTools.class.getSimpleName();

    public static void processVideo(Video video, Context context) {
        Log.d(TAG, String.format("Analysing %s", video.getName()));

        final String output = String.format("%s/%s", getResultDirPath(),
                FileManager.getResultNameFromVideoName(video.getName()));

        Intent analyseIntent = new Intent(context, VideoAnalysisIntentService.class);
        analyseIntent.putExtra(VideoAnalysisIntentService.VIDEO_KEY, video);
        analyseIntent.putExtra(VideoAnalysisIntentService.OUTPUT_KEY, output);
        context.startService(analyseIntent);

        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
    }
}
