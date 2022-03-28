package com.example.edgedashanalytics.util.video.analysis;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class OuterFrame extends Frame {
    private final List<Hazard> hazards;

    @JsonCreator
    OuterFrame(@JsonProperty("frame") int frame, @JsonProperty("hazards") List<Hazard> hazards) {
        this.frame = frame;
        this.hazards = hazards;
    }
}
