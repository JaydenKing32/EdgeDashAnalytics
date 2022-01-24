package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
class InnerFrame extends Frame {
    private final List<KeyPoint> keyPoints;

    InnerFrame(int frame, List<KeyPoint> keyPoints) {
        this.frame = frame;
        this.keyPoints = keyPoints;
    }
}
