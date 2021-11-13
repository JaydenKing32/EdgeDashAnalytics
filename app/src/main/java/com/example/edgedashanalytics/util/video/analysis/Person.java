package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Rect;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
class Person {
    private final float confidence;
    private final boolean close;
    private final Rect bBox;

    Person(float confidence, boolean close, Rect bBox) {
        this.confidence = confidence;
        this.close = close;
        this.bBox = bBox;
    }
}
