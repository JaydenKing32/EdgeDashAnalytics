package com.example.edgedashanalytics.event.video;

import com.example.edgedashanalytics.model.Video;

public class RemoveEvent extends VideoEvent {
    public RemoveEvent(Video video, Type type) {
        super(video, type);
    }
}
