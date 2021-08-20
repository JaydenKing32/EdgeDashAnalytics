package com.example.edgedashanalytics.event;

import com.example.edgedashanalytics.model.Video;

public class VideoEvent {
    public Video video;
    public Type type;

    public VideoEvent(Video video, Type type) {
        this.video = video;
        this.type = type;
    }
}
