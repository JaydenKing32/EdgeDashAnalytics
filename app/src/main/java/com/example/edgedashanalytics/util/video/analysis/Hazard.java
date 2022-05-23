package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.Rect;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

class Hazard {
    private final String category;
    private final float score;
    private final boolean danger;
    private final Rect bBox;

    @JsonCreator
    Hazard(@JsonProperty("category") String category,
           @JsonProperty("score") float score,
           @JsonProperty("danger") boolean danger,
           @JsonProperty("bBox") Rect bBox) {
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
