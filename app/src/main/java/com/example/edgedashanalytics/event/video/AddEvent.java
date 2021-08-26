package com.example.edgedashanalytics.event.video;

import com.example.edgedashanalytics.model.Video;

public class AddEvent extends VideoEvent {
    public AddEvent(Video video, Type type) {
        super(video, type);
    }
}
