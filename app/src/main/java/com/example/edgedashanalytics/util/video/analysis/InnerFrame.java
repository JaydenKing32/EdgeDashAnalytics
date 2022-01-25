package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
class InnerFrame extends Frame {
    private final float fullScore;
    private final List<KeyPoint> keyPoints;

    InnerFrame(int frame, float fullScore, List<KeyPoint> keyPoints) {
        this.frame = frame;
        this.fullScore = fullScore;
        this.keyPoints = keyPoints;
    }
}
