package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.PointF;

// @formatter:off
// https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/data/KeyPoint.kt
// @formatter:on

public class KeyPoint {
    public final BodyPart bodyPart;
    public final float score;
    public PointF coordinate;

    public KeyPoint(BodyPart bodyPart, PointF coordinate, float score) {
        this.bodyPart = bodyPart;
        this.score = score;
        this.coordinate = coordinate;
    }

    @Override
    public String toString() {
        return "KeyPoint{" +
                "bodyPart=" + bodyPart +
                ", score=" + score +
                ", coordinate=" + coordinate +
                '}';
    }
}
