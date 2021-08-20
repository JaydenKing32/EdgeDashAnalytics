package com.example.edgedashanalytics.util.video.videoeventhandler;

import android.util.Log;

import com.example.edgedashanalytics.data.VideosRepository;
import com.example.edgedashanalytics.event.AddEvent;
import com.example.edgedashanalytics.event.RemoveEvent;
import com.example.edgedashanalytics.event.Type;
import com.example.edgedashanalytics.event.VideoEvent;

import org.greenrobot.eventbus.Subscribe;

public class RawVideosEventHandler implements VideoEventHandler {
    private static final String TAG = RawVideosEventHandler.class.getSimpleName();
    public VideosRepository repository;

    public RawVideosEventHandler(VideosRepository repository) {
        this.repository = repository;
    }

    @Subscribe
    @Override
    public void onAdd(AddEvent event) {
        nullCheck(event);

        if (event.type == Type.RAW) {
            Log.v(TAG, "onAdd");
            try {
                repository.insert(event.video);
            } catch (Exception e) {
                Log.e(TAG, String.format("onAdd error: \n%s", e.getMessage()));
            }
        }
    }

    @Subscribe
    @Override
    public void onRemove(RemoveEvent event) {
        nullCheck(event);

        if (event.type == Type.RAW) {
            Log.v(TAG, "onRemove");
            try {
                repository.delete(event.video.getData());
            } catch (Exception e) {
                Log.e(TAG, String.format("onRemove error: \n%s", e.getMessage()));
            }
        }
    }

    public void nullCheck(VideoEvent event) {
        if (event == null) {
            Log.e(TAG, "Null event");
            return;
        }
        if (repository == null) {
            Log.e(TAG, "Null repository");
            return;
        }
        if (event.video == null) {
            Log.e(TAG, "Null video");
        }
    }
}
