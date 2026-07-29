package com.example.mindmap.export;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.util.TimeFormatUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 负责生成 JSON、CSV 和 HTML 报告。所有写文件调用应放在后台线程执行。
 */
public class ExportManager {
    private static final String TAG = "ExportManager";

    public File exportJson(File dir, TripEntity trip, List<TrackPointEntity> points, List<AnnotationEntity> annotations, List<RosPredictionEntity> predictions) throws IOException {
        File file = new File(dir, safeBaseName(trip) + ".json");
        Map<Long, List<RosPredictionEntity>> predictionsByAnnotation = groupPredictionsByAnnotation(predictions);
        StringBuilder builder = new StringBuilder();
        builder.append("{\n  \"trip\": ");
        appendTripJson(builder, trip);
        builder.append(",\n  \"trackPoints\": [\n");
        for (int i = 0; i < points.size(); i++) {
            appendTrackPointJson(builder, points.get(i));
            builder.append(i == points.size() - 1 ? "\n" : ",\n");
        }
        builder.append("  ],\n  \"annotations\": [\n");
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationEntity annotation = annotations.get(i);
            appendAnnotationJson(builder, annotation, predictionsByAnnotation.get(annotation.id));
            builder.append(i == annotations.size() - 1 ? "\n" : ",\n");
        }
        builder.append("  ]\n}\n");
        writeUtf8(file, builder.toString());
        return file;
    }

    public File exportCsv(File dir, TripEntity trip, List<AnnotationEntity> annotations) throws IOException {
        File file = new File(dir, safeBaseName(trip) + ".csv");
        StringBuilder builder = new StringBuilder();
        builder.append("tripId,accountName,gender,ageGroup,educationLevel,annotationId,timestamp,latitude,longitude,visualPreferenceScore,thoughtClarityScore,worryForgetScore,restoredRelaxedScore,rosCalmScore,interestScore,focusedAlertScore,rosAverageScore,pleasureScore,calmScore,relaxationScore,focusScore,averageScore,textNote,speechText,videoPath,originalVideoPath,blurredVideoPath,videoMosaicStatus,videoMosaicError,videoThumbnailPath,audioPath,cameraPitch,cameraRoll,cameraYaw,durationMillis,landscapeLabel\n");
        for (AnnotationEntity a : annotations) {
            builder.append(a.tripId).append(',')
                    .append(CsvUtils.escape(trip.accountName)).append(',')
                    .append(CsvUtils.escape(trip.gender)).append(',')
                    .append(CsvUtils.escape(trip.ageGroup)).append(',')
                    .append(CsvUtils.escape(trip.educationLevel)).append(',')
                    .append(a.id).append(',')
                    .append(a.timestamp).append(',')
                    .append(a.latitude).append(',')
                    .append(a.longitude).append(',')
                    .append(a.visualPreferenceScore).append(',')
                    .append(a.thoughtClarityScore).append(',')
                    .append(a.worryForgetScore).append(',')
                    .append(a.restoredRelaxedScore).append(',')
                    .append(a.rosCalmScore).append(',')
                    .append(a.interestScore).append(',')
                    .append(a.focusedAlertScore).append(',')
                    .append(a.rosAverageScore).append(',')
                    .append(a.pleasureScore).append(',')
                    .append(a.calmScore).append(',')
                    .append(a.relaxationScore).append(',')
                    .append(a.focusScore).append(',')
                    .append(a.averageScore).append(',')
                    .append(CsvUtils.escape(a.textNote)).append(',')
                    .append(CsvUtils.escape(a.speechText)).append(',')
                    .append(CsvUtils.escape(a.videoUri)).append(',')
                    .append(CsvUtils.escape(a.originalVideoUri)).append(',')
                    .append(CsvUtils.escape(a.blurredVideoUri)).append(',')
                    .append(CsvUtils.escape(a.videoMosaicStatus)).append(',')
                    .append(CsvUtils.escape(a.videoMosaicError)).append(',')
                    .append(CsvUtils.escape(a.videoThumbnailUri)).append(',')
                    .append(CsvUtils.escape(a.audioUri)).append(',')
                    .append(a.cameraPitch).append(',')
                    .append(a.cameraRoll).append(',')
                    .append(a.cameraYaw).append(',')
                    .append(a.durationMillis).append(',')
                    .append(CsvUtils.escape(a.landscapeLabel)).append('\n');
        }
        writeUtf8(file, builder.toString());
        return file;
    }

    public File exportHtml(File dir, TripEntity trip, List<AnnotationEntity> annotations) throws IOException {
        File file = new File(dir, safeBaseName(trip) + ".html");
        MoodStats stats = MoodStats.from(annotations);
        AnnotationEntity best = null;
        for (AnnotationEntity annotation : annotations) {
            if (best == null || annotation.rosAverageScore > best.rosAverageScore) {
                best = annotation;
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><title>心境地图报告</title>")
                .append("<style>body{font-family:Arial,'Microsoft YaHei',sans-serif;margin:32px;color:#1b2b1f}table{border-collapse:collapse;width:100%;margin-top:16px}td,th{border:1px solid #cfd8dc;padding:8px;text-align:left}.ok{color:#2e7d32}.card{border:1px solid #d8e6da;border-radius:8px;padding:16px;margin:12px 0}</style>")
                .append("</head><body><h1>心境地图分析报告</h1>")
                .append("<div class=\"card\"><h2>").append(html(trip.name)).append("</h2>")
                .append("<p>账户：").append(html(trip.accountName)).append("</p>")
                .append("<p>基本信息：").append(html(trip.gender)).append(" / ").append(html(trip.ageGroup)).append(" / ").append(html(trip.educationLevel)).append("</p>")
                .append("<p>日期：").append(TimeFormatUtils.readableDate(trip.startTime)).append("</p>")
                .append("<p>时长：").append(TimeFormatUtils.duration(trip.durationMillis)).append("，距离：").append(String.format(Locale.CHINA, "%.1f 米", trip.distanceMeters)).append("，手动标记：").append(trip.annotationCount).append("处</p></div>")
                .append("<h2>ROS恢复性感知统计</h2><p>有效样本数：").append(stats.count).append("</p><ul>");
        if (stats.count == 1 && !annotations.isEmpty()) {
            AnnotationEntity annotation = annotations.get(0);
            builder.append("<li>本次评价</li>");
            for (int i = 0; i < MoodStats.ROS_LABELS.length; i++) {
                builder.append("<li>").append(html(MoodStats.ROS_LABELS[i]))
                        .append(" ").append(String.format(Locale.CHINA, "%.1f", (float) rosScore(annotation, i)))
                        .append("/5</li>");
            }
        } else {
            for (int i = 0; i < MoodStats.ROS_LABELS.length; i++) {
                builder.append("<li>").append(html(MoodStats.ROS_LABELS[i]))
                        .append(" 平均 ").append(stats.averages[i]).append("/5")
                        .append(" 范围 ").append(stats.minScores[i]).append("-").append(stats.maxScores[i])
                        .append("</li>");
            }
        }
        builder.append("</ul>");
        if (best != null) {
            builder.append("<p class=\"ok\">ROS得分最高的手动标记：").append(TimeFormatUtils.readableDate(best.timestamp)).append("，平均分 ").append(best.rosAverageScore).append("/5</p>");
        }
        builder.append("<h2>手动标记表格</h2><table><tr><th>时间</th><th>位置</th><th>评分</th><th>文字</th><th>语音转写</th><th>视频/音频</th><th>角度/时长</th></tr>");
        for (AnnotationEntity a : annotations) {
            builder.append("<tr><td>").append(TimeFormatUtils.readableDate(a.timestamp)).append("</td><td>")
                    .append(a.latitude).append(", ").append(a.longitude).append("</td><td>")
                    .append(a.visualPreferenceScore).append('/')
                    .append(a.thoughtClarityScore).append('/')
                    .append(a.worryForgetScore).append('/')
                    .append(a.restoredRelaxedScore).append('/')
                    .append(a.rosCalmScore).append('/')
                    .append(a.interestScore).append('/')
                    .append(a.focusedAlertScore).append("</td><td>")
                    .append(html(a.textNote)).append("</td><td>").append(html(a.speechText)).append("</td><td>")
                    .append(html(a.videoUri)).append("<br>原视频：").append(html(a.originalVideoUri))
                    .append("<br>打码视频：").append(html(a.blurredVideoUri))
                    .append("<br>打码状态：").append(html(a.videoMosaicStatus))
                    .append("<br>缩略图：").append(html(a.videoThumbnailUri)).append("<br>").append(html(a.audioUri)).append("</td><td>")
                    .append("Pitch ").append(a.cameraPitch).append("° / Roll ").append(a.cameraRoll).append("° / Yaw ").append(a.cameraYaw)
                    .append("°<br>媒体时长：").append(TimeFormatUtils.duration(a.durationMillis)).append("</td></tr>");
        }
        builder.append("</table><h2>数据采集说明</h2><p>报告由 APP 本地生成。DEMO 数据仅用于演示，最终课程验收需要用户在公园实际步行采集。</p></body></html>");
        writeUtf8(file, builder.toString());
        return file;
    }

    public Intent buildShareIntent(Context context, File file, String mimeType) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(intent, "分享导出文件");
    }

    private void appendTripJson(StringBuilder builder, TripEntity trip) {
        builder.append("{\n")
                .append("    \"id\": ").append(trip.id).append(",\n")
                .append("    \"name\": \"").append(json(trip.name)).append("\",\n")
                .append("    \"destination\": \"").append(json(trip.destination)).append("\",\n")
                .append("    \"recordMode\": \"").append(json(trip.recordMode)).append("\",\n")
                .append("    \"startTime\": ").append(trip.startTime).append(",\n")
                .append("    \"endTime\": ").append(trip.endTime).append(",\n")
                .append("    \"durationMillis\": ").append(trip.durationMillis).append(",\n")
                .append("    \"distanceMeters\": ").append(trip.distanceMeters).append(",\n")
                .append("    \"annotationCount\": ").append(trip.annotationCount).append(",\n")
                .append("    \"status\": \"").append(json(trip.status)).append("\",\n")
                .append("    \"createdAt\": ").append(trip.createdAt).append(",\n")
                .append("    \"userProfileSnapshot\": {\n")
                .append("      \"accountName\": \"").append(json(trip.accountName)).append("\",\n")
                .append("      \"gender\": \"").append(json(trip.gender)).append("\",\n")
                .append("      \"ageGroup\": \"").append(json(trip.ageGroup)).append("\",\n")
                .append("      \"educationLevel\": \"").append(json(trip.educationLevel)).append("\"\n")
                .append("    }\n")
                .append("  }");
    }

    private void appendTrackPointJson(StringBuilder builder, TrackPointEntity p) {
        builder.append("    {\n")
                .append("      \"id\": ").append(p.id).append(",\n")
                .append("      \"tripId\": ").append(p.tripId).append(",\n")
                .append("      \"latitude\": ").append(p.latitude).append(",\n")
                .append("      \"longitude\": ").append(p.longitude).append(",\n")
                .append("      \"altitude\": ").append(p.altitude).append(",\n")
                .append("      \"accuracy\": ").append(p.accuracy).append(",\n")
                .append("      \"speed\": ").append(p.speed).append(",\n")
                .append("      \"bearing\": ").append(p.bearing).append(",\n")
                .append("      \"timestamp\": ").append(p.timestamp).append(",\n")
                .append("      \"accelerationX\": ").append(p.accelerationX).append(",\n")
                .append("      \"accelerationY\": ").append(p.accelerationY).append(",\n")
                .append("      \"accelerationZ\": ").append(p.accelerationZ).append(",\n")
                .append("      \"pitch\": ").append(p.pitch).append(",\n")
                .append("      \"roll\": ").append(p.roll).append(",\n")
                .append("      \"yaw\": ").append(p.yaw).append(",\n")
                .append("      \"movingState\": \"").append(json(p.movingState)).append("\"\n")
                .append("    }");
    }

    private void appendAnnotationJson(StringBuilder builder, AnnotationEntity a, List<RosPredictionEntity> predictions) {
        builder.append("    {\n")
                .append("      \"id\": ").append(a.id).append(",\n")
                .append("      \"tripId\": ").append(a.tripId).append(",\n")
                .append("      \"latitude\": ").append(a.latitude).append(",\n")
                .append("      \"longitude\": ").append(a.longitude).append(",\n")
                .append("      \"timestamp\": ").append(a.timestamp).append(",\n")
                .append("      \"videoUri\": \"").append(json(a.videoUri)).append("\",\n")
                .append("      \"originalVideoUri\": \"").append(json(a.originalVideoUri)).append("\",\n")
                .append("      \"blurredVideoUri\": \"").append(json(a.blurredVideoUri)).append("\",\n")
                .append("      \"videoMosaicStatus\": \"").append(json(a.videoMosaicStatus)).append("\",\n")
                .append("      \"videoMosaicError\": \"").append(json(a.videoMosaicError)).append("\",\n")
                .append("      \"videoThumbnailUri\": \"").append(json(a.videoThumbnailUri)).append("\",\n")
                .append("      \"audioUri\": \"").append(json(a.audioUri)).append("\",\n")
                .append("      \"speechText\": \"").append(json(a.speechText)).append("\",\n")
                .append("      \"textNote\": \"").append(json(a.textNote)).append("\",\n")
                .append("      \"description\": ").append(nullableJson(a.textNote)).append(",\n")
                .append("      \"transcription\": ").append(nullableJson(a.speechText)).append(",\n")
                .append("      \"visualPreferenceScore\": ").append(a.visualPreferenceScore).append(",\n")
                .append("      \"thoughtClarityScore\": ").append(a.thoughtClarityScore).append(",\n")
                .append("      \"worryForgetScore\": ").append(a.worryForgetScore).append(",\n")
                .append("      \"restoredRelaxedScore\": ").append(a.restoredRelaxedScore).append(",\n")
                .append("      \"rosCalmScore\": ").append(a.rosCalmScore).append(",\n")
                .append("      \"interestScore\": ").append(a.interestScore).append(",\n")
                .append("      \"focusedAlertScore\": ").append(a.focusedAlertScore).append(",\n")
                .append("      \"rosAverageScore\": ").append(a.rosAverageScore).append(",\n")
                .append("      \"pleasureScore\": ").append(a.pleasureScore).append(",\n")
                .append("      \"calmScore\": ").append(a.calmScore).append(",\n")
                .append("      \"relaxationScore\": ").append(a.relaxationScore).append(",\n")
                .append("      \"focusScore\": ").append(a.focusScore).append(",\n")
                .append("      \"averageScore\": ").append(a.averageScore).append(",\n")
                .append("      \"cameraPitch\": ").append(a.cameraPitch).append(",\n")
                .append("      \"cameraRoll\": ").append(a.cameraRoll).append(",\n")
                .append("      \"cameraYaw\": ").append(a.cameraYaw).append(",\n")
                .append("      \"durationMillis\": ").append(a.durationMillis).append(",\n")
                .append("      \"landscapeLabel\": \"").append(json(a.landscapeLabel)).append("\",\n")
                .append("      \"createdAt\": ").append(a.createdAt).append(",\n")
                .append("      \"rosPredictions\": [");
        appendRosPredictionsJson(builder, predictions);
        if (predictions != null && !predictions.isEmpty()) {
            builder.append("\n      ");
        }
        builder.append("]\n")
                .append("    }");
    }

    private Map<Long, List<RosPredictionEntity>> groupPredictionsByAnnotation(List<RosPredictionEntity> predictions) {
        Map<Long, List<RosPredictionEntity>> grouped = new HashMap<>();
        if (predictions == null) {
            return grouped;
        }
        for (RosPredictionEntity prediction : predictions) {
            grouped.computeIfAbsent(prediction.annotationId, key -> new ArrayList<>()).add(prediction);
        }
        return grouped;
    }

    private void appendRosPredictionsJson(StringBuilder builder, List<RosPredictionEntity> predictions) {
        if (predictions == null || predictions.isEmpty()) {
            return;
        }
        for (int i = 0; i < predictions.size(); i++) {
            RosPredictionEntity p = predictions.get(i);
            builder.append("\n        {\n")
                    .append("          \"id\": ").append(p.id).append(",\n")
                    .append("          \"annotationId\": ").append(p.annotationId).append(",\n")
                    .append("          \"transcript\": \"").append(json(p.transcript)).append("\",\n")
                    .append("          \"visualPreferenceScore\": ").append(p.visualPreferenceScore).append(",\n")
                    .append("          \"thoughtClarityScore\": ").append(p.thoughtClarityScore).append(",\n")
                    .append("          \"worryForgetScore\": ").append(p.worryForgetScore).append(",\n")
                    .append("          \"restoredRelaxedScore\": ").append(p.restoredRelaxedScore).append(",\n")
                    .append("          \"rosCalmScore\": ").append(p.rosCalmScore).append(",\n")
                    .append("          \"interestScore\": ").append(p.interestScore).append(",\n")
                    .append("          \"focusedAlertScore\": ").append(p.focusedAlertScore).append(",\n")
                    .append("          \"keywords\": ").append(jsonArrayOrEmpty(p.keywordsJson)).append(",\n")
                    .append("          \"keywordsJson\": \"").append(json(p.keywordsJson)).append("\",\n")
                    .append("          \"reason\": \"").append(json(p.reason)).append("\",\n")
                    .append("          \"modelName\": \"").append(json(p.modelName)).append("\",\n")
                    .append("          \"modelVersion\": \"").append(json(p.modelVersion)).append("\",\n")
                    .append("          \"promptVersion\": \"").append(json(p.promptVersion)).append("\",\n")
                    .append("          \"status\": \"").append(json(p.status)).append("\",\n")
                    .append("          \"errorMessage\": \"").append(json(p.errorMessage)).append("\",\n")
                    .append("          \"createdAt\": ").append(p.createdAt).append("\n")
                    .append("        }");
            if (i < predictions.size() - 1) {
                builder.append(",");
            }
        }
    }

    private String safeBaseName(TripEntity trip) {
        String name = trip.name == null ? "未命名行程" : trip.name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return name + "_" + TimeFormatUtils.fileSafeDate(trip.startTime);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String nullableJson(String value) {
        return isBlank(value) ? "null" : "\"" + json(value.trim()) + "\"";
    }

    private static String jsonArrayOrEmpty(String value) {
        if (isBlank(value)) {
            return "[]";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]") ? trimmed : "[]";
    }

    private static int rosScore(AnnotationEntity annotation, int index) {
        switch (index) {
            case 0:
                return annotation.visualPreferenceScore;
            case 1:
                return annotation.thoughtClarityScore;
            case 2:
                return annotation.worryForgetScore;
            case 3:
                return annotation.restoredRelaxedScore;
            case 4:
                return annotation.rosCalmScore;
            case 5:
                return annotation.interestScore;
            case 6:
                return annotation.focusedAlertScore;
            default:
                return 0;
        }
    }

    private static String json(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String html(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void writeUtf8(File file, String content) throws IOException {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            Log.e(TAG, "导出文件失败: " + file, exception);
            throw exception;
        }
    }
}
