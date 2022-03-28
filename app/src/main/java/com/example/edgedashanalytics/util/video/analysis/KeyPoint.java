package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.PointF;

import androidx.annotation.NonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/data/KeyPoint.kt
class KeyPoint {
    final BodyPart bodyPart;
    final float score;
    PointF coordinate;

    @JsonCreator
    KeyPoint(@JsonProperty("bodyPart") BodyPart bodyPart,
             @JsonProperty("coordinate") PointF coordinate,
             @JsonProperty("score") float score) {
        this.bodyPart = bodyPart;
        this.score = score;
        this.coordinate = coordinate;
    }

    @NonNull
    @Override
    public String toString() {
        return "KeyPoint{" +
                "bodyPart=" + bodyPart +
                ", score=" + score +
                ", coordinate=" + coordinate +
                '}';
    }
}
