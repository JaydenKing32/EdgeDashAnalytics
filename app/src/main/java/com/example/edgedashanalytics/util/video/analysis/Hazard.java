package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Rect;

import androidx.annotation.NonNull;

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

    @NonNull
    @Override
    public String toString() {
        return "Hazard{" +
                "category='" + category + '\'' +
                ", score=" + score +
                ", danger=" + danger +
                ", bBox=" + bBox +
                '}';
    }
}
