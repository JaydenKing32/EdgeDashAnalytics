package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class OuterFrame extends Frame {
    private final List<Hazard> hazards;

    OuterFrame(int frame, List<Hazard> hazards) {
        this.frame = frame;
        this.hazards = hazards;
    }
}
