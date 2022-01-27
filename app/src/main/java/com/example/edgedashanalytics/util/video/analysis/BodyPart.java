package com.example.edgedashanalytics.util.video.analysis;

// https://github.com/tensorflow/examples/blob/master/lite/examples/pose_estimation/android/app/src/main/java/org/tensorflow/lite/examples/poseestimation/data/BodyPart.kt
public enum BodyPart {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE;

    public static final BodyPart[] AS_ARRAY = BodyPart.values();
    public static final int LOWER_INDEX = LEFT_HIP.ordinal();
}
