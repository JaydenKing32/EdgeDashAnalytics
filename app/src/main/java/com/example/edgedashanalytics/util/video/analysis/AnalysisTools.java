package com.example.edgedashanalytics.util.video.analysis;

import static com.example.edgedashanalytics.util.file.FileManager.getResultDirPath;

import android.content.Context;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

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

        WorkRequest analysisWorkRequest = new OneTimeWorkRequest.Builder(AnalysisWorker.class)
                .setInputData(new Data.Builder()
                        .putString(AnalysisWorker.IN_KEY, video.getData())
                        .putString(AnalysisWorker.OUT_KEY, output).build())
                .build();
        WorkManager workManager = WorkManager.getInstance(context);
        workManager.enqueue(analysisWorkRequest);

        EventBus.getDefault().post(new AddEvent(video, Type.PROCESSING));
        EventBus.getDefault().post(new RemoveEvent(video, Type.RAW));
    }
}
