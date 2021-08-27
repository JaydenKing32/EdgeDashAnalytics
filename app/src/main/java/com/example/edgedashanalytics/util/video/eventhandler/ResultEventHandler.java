package com.example.edgedashanalytics.util.video.eventhandler;

import android.util.Log;

import com.example.edgedashanalytics.data.result.ResultRepository;
import com.example.edgedashanalytics.event.result.AddResultEvent;
import com.example.edgedashanalytics.event.result.RemoveResultEvent;
import com.example.edgedashanalytics.event.result.ResultEvent;

import org.greenrobot.eventbus.Subscribe;

public class ResultEventHandler {
    private static final String TAG = ResultEventHandler.class.getSimpleName();
    private final ResultRepository repository;

    public ResultEventHandler(ResultRepository repository) {
        this.repository = repository;
    }

    @Subscribe
    public void onAdd(AddResultEvent event) {
        nullCheck(event);

        Log.v(TAG, "onAdd");
        try {
            repository.insert(event.result);
        } catch (Exception e) {
            Log.e(TAG, String.format("onAdd error: \n%s", e.getMessage()));
        }
    }

    @Subscribe
    public void onRemove(RemoveResultEvent event) {
        nullCheck(event);

        Log.v(TAG, "onRemove");
        try {
            repository.delete(event.result.getData());
        } catch (Exception e) {
            Log.e(TAG, String.format("onRemove error: \n%s", e.getMessage()));
        }
    }

    private void nullCheck(ResultEvent event) {
        if (event == null) {
            Log.e(TAG, "Null event");
            return;
        }
        if (repository == null) {
            Log.e(TAG, "Null repository");
            return;
        }
        if (event.result == null) {
            Log.e(TAG, "Null result");
        }
    }
}
