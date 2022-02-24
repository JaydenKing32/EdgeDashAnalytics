package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class InnerFrame extends Frame {
    private final boolean distracted;
    private final float fullScore;
    private final List<KeyPoint> keyPoints;

    InnerFrame(int frame, boolean distracted, float fullScore, List<KeyPoint> keyPoints) {
        this.frame = frame;
        this.distracted = distracted;
        this.fullScore = fullScore;
        this.keyPoints = keyPoints;
    }
}
