package com.example.mindmap.util;

/** 集中管理采集、媒体和过滤阈值，便于课程答辩时说明取值依据。 */
public final class AppConstants {
    public static final String RECORD_MODE_AUTO = "AUTO";
    public static final String RECORD_MODE_MANUAL = "MANUAL";
    public static final String TRIP_STATUS_ACTIVE = "ACTIVE";
    public static final String TRIP_STATUS_FINISHED = "FINISHED";
    public static final String PREDICTION_STATUS_SUCCESS = "SUCCESS";
    public static final String PREDICTION_STATUS_FAILED = "FAILED";
    public static final String MOVING = "MOVING";
    public static final String STAYING = "STAYING";
    public static final String UNKNOWN = "UNKNOWN";
    public static final long LOCATION_INTERVAL_MILLIS = 1500L;
    public static final float MAX_ACCEPTED_ACCURACY_METERS = 60f;
    public static final float MAX_REASONABLE_SPEED_MPS = 6f;
    public static final long MAX_VIDEO_DURATION_MILLIS = 60_000L;
    public static final long MAX_AUDIO_DURATION_MILLIS = 60_000L;
    public static final int MAX_NOTE_LENGTH = 1000;
    private AppConstants() {}
}
