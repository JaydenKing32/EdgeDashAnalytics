package com.example.edgedashanalytics.util.video.analysis;

import android.graphics.PointF;

// https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/data/KeyPoint.kt
class KeyPoint {
    final BodyPart bodyPart;
    final float score;
    PointF coordinate;

    KeyPoint(BodyPart bodyPart, PointF coordinate, float score) {
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
