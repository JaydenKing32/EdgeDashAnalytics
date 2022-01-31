package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Rect;

@SuppressWarnings({"FieldCanBeLocal", "unused"})
class Hazard {
    private final String category;
    private final float score;
    private final boolean danger;
    private final Rect bBox;

    Hazard(String category, float score, boolean danger, Rect bBox) {
        this.category = category;
        this.score = score;
        this.danger = danger;
        this.bBox = bBox;
    }
}
