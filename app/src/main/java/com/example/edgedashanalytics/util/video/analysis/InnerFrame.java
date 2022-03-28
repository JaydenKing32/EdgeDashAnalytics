package com.example.edgedashanalytics.util.video.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class InnerFrame extends Frame {
    private final boolean distracted;
    private final float fullScore;
    private final List<KeyPoint> keyPoints;

    @JsonCreator
    InnerFrame(@JsonProperty("frame") int frame,
               @JsonProperty("distracted") boolean distracted,
               @JsonProperty("fullScore") float fullScore,
               @JsonProperty("keyPoints") List<KeyPoint> keyPoints) {
        this.frame = frame;
        this.distracted = distracted;
        this.fullScore = fullScore;
        this.keyPoints = keyPoints;
    }
}
