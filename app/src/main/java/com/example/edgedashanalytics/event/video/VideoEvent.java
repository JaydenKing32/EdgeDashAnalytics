package com.example.edgedashanalytics.event.video;

import com.example.edgedashanalytics.model.Video;

public class VideoEvent {
    public final Video video;
    public final Type type;

    VideoEvent(Video video, Type type) {
        this.video = video;
        this.type = type;
    }
}
