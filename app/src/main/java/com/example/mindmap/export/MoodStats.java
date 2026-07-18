package com.example.mindmap.export;

import com.example.mindmap.data.local.entity.AnnotationEntity;

import java.util.List;
import java.util.Locale;

/** 计算详情页和 HTML 报告共用的四维情绪统计。 */
public class MoodStats {
    public static final String[] ROS_LABELS = {
            "Q1 视觉偏好",
            "Q2 思绪清晰",
            "Q3 忘记烦恼",
            "Q4 放松恢复",
            "Q5 平静感",
            "Q6 兴趣唤起",
            "Q7 注意恢复"
    };

    public int count;
    public final int[] minScores = {5, 5, 5, 5, 5, 5, 5};
    public final int[] maxScores = {1, 1, 1, 1, 1, 1, 1};
    public final String[] averages = {"0.0", "0.0", "0.0", "0.0", "0.0", "0.0", "0.0"};
    public int minPleasure = 5;
    public int maxPleasure = 1;
    public int minCalm = 5;
    public int maxCalm = 1;
    public int minRelaxation = 5;
    public int maxRelaxation = 1;
    public int minFocus = 5;
    public int maxFocus = 1;
    public String pleasureAverage = "0.0";
    public String calmAverage = "0.0";
    public String relaxationAverage = "0.0";
    public String focusAverage = "0.0";

    public static MoodStats from(List<AnnotationEntity> annotations) {
        MoodStats stats = new MoodStats();
        if (annotations == null || annotations.isEmpty()) {
            for (int i = 0; i < stats.minScores.length; i++) {
                stats.minScores[i] = 0;
                stats.maxScores[i] = 0;
            }
            stats.minPleasure = stats.minCalm = stats.minRelaxation = stats.minFocus = 0;
            stats.maxPleasure = stats.maxCalm = stats.maxRelaxation = stats.maxFocus = 0;
            return stats;
        }
        int[] totals = new int[7];
        int pleasure = 0;
        int calm = 0;
        int relaxation = 0;
        int focus = 0;
        for (AnnotationEntity a : annotations) {
            stats.count++;
            int[] scores = {
                    a.visualPreferenceScore,
                    a.thoughtClarityScore,
                    a.worryForgetScore,
                    a.restoredRelaxedScore,
                    a.rosCalmScore,
                    a.interestScore,
                    a.focusedAlertScore
            };
            for (int i = 0; i < scores.length; i++) {
                totals[i] += scores[i];
                stats.minScores[i] = Math.min(stats.minScores[i], scores[i]);
                stats.maxScores[i] = Math.max(stats.maxScores[i], scores[i]);
            }
            pleasure += a.pleasureScore;
            calm += a.calmScore;
            relaxation += a.relaxationScore;
            focus += a.focusScore;
            stats.minPleasure = Math.min(stats.minPleasure, a.pleasureScore);
            stats.maxPleasure = Math.max(stats.maxPleasure, a.pleasureScore);
            stats.minCalm = Math.min(stats.minCalm, a.calmScore);
            stats.maxCalm = Math.max(stats.maxCalm, a.calmScore);
            stats.minRelaxation = Math.min(stats.minRelaxation, a.relaxationScore);
            stats.maxRelaxation = Math.max(stats.maxRelaxation, a.relaxationScore);
            stats.minFocus = Math.min(stats.minFocus, a.focusScore);
            stats.maxFocus = Math.max(stats.maxFocus, a.focusScore);
        }
        stats.pleasureAverage = String.format(Locale.CHINA, "%.1f", pleasure / (float) stats.count);
        stats.calmAverage = String.format(Locale.CHINA, "%.1f", calm / (float) stats.count);
        stats.relaxationAverage = String.format(Locale.CHINA, "%.1f", relaxation / (float) stats.count);
        stats.focusAverage = String.format(Locale.CHINA, "%.1f", focus / (float) stats.count);
        for (int i = 0; i < totals.length; i++) {
            stats.averages[i] = String.format(Locale.CHINA, "%.1f", totals[i] / (float) stats.count);
        }
        return stats;
    }
}
