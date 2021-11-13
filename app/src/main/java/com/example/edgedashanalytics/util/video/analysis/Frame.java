package com.example.edgedashanalytics.util.video.analysis;

import java.util.List;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class Frame {
    public int frame;
    private final List<Plate> plates;

    Frame(int frame, List<Plate> plates) {
        this.frame = frame;
        this.plates = plates;
    }
}
