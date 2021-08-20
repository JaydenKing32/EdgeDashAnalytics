package com.example.edgedashanalytics.util.video.videoeventhandler;

import com.example.edgedashanalytics.event.AddEvent;
import com.example.edgedashanalytics.event.RemoveEvent;

public interface VideoEventHandler {
    void onAdd(AddEvent event);

    void onRemove(RemoveEvent event);
}
