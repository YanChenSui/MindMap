package com.example.mindmap.ui.tripdetail.section;

import android.content.Context;
import android.widget.LinearLayout;

import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.ui.UiFactory;

import java.util.List;

/** Renders user ROS scores and model prediction history for one annotation. */
public final class RosAnnotationSection {
    private RosAnnotationSection() {
    }

    public static void addTo(Context context, LinearLayout body, AnnotationEntity annotation,
                             List<RosPredictionEntity> predictions) {
        body.addView(UiFactory.cardTitle(context, "用户评价"));
        body.addView(UiFactory.body(context, "ROS评分："
                + annotation.visualPreferenceScore + "/"
                + annotation.thoughtClarityScore + "/"
                + annotation.worryForgetScore + "/"
                + annotation.restoredRelaxedScore + "/"
                + annotation.rosCalmScore + "/"
                + annotation.interestScore + "/"
                + annotation.focusedAlertScore));

        body.addView(UiFactory.cardTitle(context, "模型预测"));
        if (predictions == null || predictions.isEmpty()) {
            body.addView(UiFactory.mutedText(context, "暂无模型预测"));
            return;
        }
        for (RosPredictionEntity prediction : predictions) {
            body.addView(UiFactory.mutedText(context, safe(prediction.status) + "  "
                    + safe(prediction.modelName) + " / " + safe(prediction.modelVersion)
                    + " / " + safe(prediction.promptVersion)));
            if ("SUCCESS".equals(prediction.status)) {
                body.addView(UiFactory.body(context, "预测评分："
                        + prediction.visualPreferenceScore + "/"
                        + prediction.thoughtClarityScore + "/"
                        + prediction.worryForgetScore + "/"
                        + prediction.restoredRelaxedScore + "/"
                        + prediction.rosCalmScore + "/"
                        + prediction.interestScore + "/"
                        + prediction.focusedAlertScore));
                body.addView(UiFactory.mutedText(context, "关键词：" + safe(prediction.keywordsJson)));
                body.addView(UiFactory.mutedText(context, "依据：" + safe(prediction.reason)));
            } else {
                body.addView(UiFactory.mutedText(context, "错误：" + safe(prediction.errorMessage)));
            }
        }
    }

    private static String safe(String value) {
        return value == null || value.isEmpty() ? "无" : value;
    }
}
