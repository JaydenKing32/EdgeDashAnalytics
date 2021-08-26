package com.example.edgedashanalytics.util.video.eventhandler;

import com.example.edgedashanalytics.event.video.AddEvent;
import com.example.edgedashanalytics.event.video.RemoveEvent;

public interface VideoEventHandler {
    void onAdd(AddEvent event);

    void onRemove(RemoveEvent event);
}
