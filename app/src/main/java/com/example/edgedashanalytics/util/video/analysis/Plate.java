package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Rect;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
public class Plate {
    private final String text;
    private final Rect bBox;

    public Plate(String text, Rect bBox) {
        this.text = text;
        this.bBox = bBox;
    }
}
