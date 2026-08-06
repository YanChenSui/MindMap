package com.example.mindmap.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** 统一处理页面、导出文件和地图标注使用的时间、距离、坐标展示格式。 */
public final class TimeFormatUtils {
    private TimeFormatUtils() {}

    public static String fileSafeDate(long timeMillis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date(timeMillis));
    }

    public static String readableDate(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(timeMillis));
    }

    /** Displays an absolute wall-clock time with seconds, milliseconds and the current time-zone name. */
    public static String worldDateTime(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z", Locale.CHINA).format(new Date(timeMillis));
    }

    /** Stable UTC representation for JSON/CSV exchange across devices and servers. */
    public static String utcIso8601(long timeMillis) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        return formatter.format(new Date(timeMillis));
    }

    public static String speechTimeRange(Long startTimeMillis, Long endTimeMillis) {
        if (startTimeMillis == null || endTimeMillis == null) {
            return "未检测到";
        }
        return worldDateTime(startTimeMillis) + " ～ " + worldDateTime(endTimeMillis);
    }

    public static String dateOnly(long timeMillis) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(timeMillis));
    }

    public static String timeOnly(long timeMillis) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(timeMillis));
    }

    public static String timeRange(long startTime, long endTime) {
        long safeEndTime = endTime > 0L ? endTime : System.currentTimeMillis();
        return timeOnly(startTime) + "-" + timeOnly(safeEndTime);
    }

    /** 简短时长用于概览指标：1 分 18 秒、1 小时 12 分钟。 */
    public static String duration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return minutes == 0L ? hours + "小时" : hours + "小时" + minutes + "分钟";
        }
        if (minutes > 0L) {
            return seconds == 0L ? minutes + "分钟" : minutes + "分" + seconds + "秒";
        }
        return seconds + "秒";
    }

    public static String distance(double meters) {
        double safeMeters = Math.max(0d, meters);
        if (safeMeters >= 1000d) {
            return String.format(Locale.CHINA, "%.2f km", safeMeters / 1000d);
        }
        return String.format(Locale.CHINA, "%.0f m", safeMeters);
    }

    public static String averageSpeedKmh(double meters, long durationMillis) {
        if (meters <= 0d || durationMillis <= 0L) {
            return "0.0 km/h";
        }
        double kmh = meters / (durationMillis / 1000d) * 3.6d;
        return String.format(Locale.CHINA, "%.1f km/h", kmh);
    }

    public static String coordinate(double value) {
        return String.format(Locale.CHINA, "%.5f", value);
    }

    public static String tripStatus(String status) {
        if (AppConstants.TRIP_STATUS_ACTIVE.equals(status)) {
            return "进行中";
        }
        if (AppConstants.TRIP_STATUS_FINISHED.equals(status)) {
            return "已完成";
        }
        return "未知";
    }

    public static String recordMode(String recordMode) {
        if (AppConstants.RECORD_MODE_AUTO.equals(recordMode)) {
            return "自动连续记录";
        }
        if (AppConstants.RECORD_MODE_MANUAL.equals(recordMode)) {
            return "手动触发记录";
        }
        return "未设置";
    }

    public static String movingState(String movingState) {
        if (AppConstants.MOVING.equals(movingState)) {
            return "移动中";
        }
        if (AppConstants.STAYING.equals(movingState)) {
            return "停留";
        }
        return "未知";
    }
}
