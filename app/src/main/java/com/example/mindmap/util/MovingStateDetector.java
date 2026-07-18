package com.example.mindmap.util;

/** 通过速度和加速度粗略判断移动/停留状态，避免把高频传感器事件直接写数据库。 */
public final class MovingStateDetector {
    private static final float MOVING_SPEED_MPS = 0.7f;
    private static final float ACCELERATION_DELTA_THRESHOLD = 1.3f;

    private MovingStateDetector() {}

    public static String detect(float speedMetersPerSecond, float ax, float ay, float az) {
        double magnitude = Math.sqrt(ax * ax + ay * ay + az * az);
        double deltaFromGravity = Math.abs(magnitude - 9.80665);
        if (speedMetersPerSecond >= MOVING_SPEED_MPS || deltaFromGravity >= ACCELERATION_DELTA_THRESHOLD) {
            return AppConstants.MOVING;
        }
        if (speedMetersPerSecond >= 0f) {
            return AppConstants.STAYING;
        }
        return AppConstants.UNKNOWN;
    }
}
