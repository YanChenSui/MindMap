package com.example.mindmap.ui.tripdetail;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.export.MoodStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native Canvas chart showing ROS scores as mobile-friendly horizontal bars. */
public class MoodBarChartView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<AnnotationEntity> annotations = new ArrayList<>();
    private final float[] sums = new float[7];

    public MoodBarChartView(Context context) {
        super(context);
        setMinimumHeight(360);
    }

    public void setAnnotations(List<AnnotationEntity> values) {
        annotations.clear();
        if (values != null) {
            annotations.addAll(values);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        paint.setColor(Color.rgb(232, 245, 233));
        canvas.drawRect(0, 0, width, height, paint);
        if (annotations.isEmpty()) {
            paint.setColor(Color.rgb(80, 100, 86));
            paint.setTextSize(38f);
            canvas.drawText("暂无情绪数据", 32, height / 2f, paint);
            return;
        }
        for (int i = 0; i < sums.length; i++) {
            sums[i] = 0f;
        }
        for (AnnotationEntity annotation : annotations) {
            sums[0] += annotation.visualPreferenceScore;
            sums[1] += annotation.thoughtClarityScore;
            sums[2] += annotation.worryForgetScore;
            sums[3] += annotation.restoredRelaxedScore;
            sums[4] += annotation.rosCalmScore;
            sums[5] += annotation.interestScore;
            sums[6] += annotation.focusedAlertScore;
        }
        String[] labels = MoodStats.ROS_LABELS;
        float leftPadding = 24f;
        float rightPadding = 24f;
        float topPadding = 22f;
        float rowHeight = Math.max(42f, (height - topPadding * 2f) / labels.length);
        float labelWidth = Math.min(150f, width * 0.34f);
        float valueWidth = 58f;
        float barLeft = leftPadding + labelWidth + 8f;
        float barRight = width - rightPadding - valueWidth;
        float barHeight = 14f;
        for (int i = 0; i < labels.length; i++) {
            float avg = sums[i] / annotations.size();
            float centerY = topPadding + rowHeight * i + rowHeight / 2f;

            paint.setColor(Color.rgb(27, 43, 31));
            paint.setTextSize(24f);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(labels[i], leftPadding, centerY + 8f, paint);

            paint.setColor(Color.rgb(211, 225, 215));
            canvas.drawRoundRect(barLeft, centerY - barHeight / 2f, barRight,
                    centerY + barHeight / 2f, barHeight / 2f, barHeight / 2f, paint);

            paint.setColor(Color.rgb(46, 125, 50));
            float filledRight = barLeft + (barRight - barLeft) * Math.max(0f, Math.min(5f, avg)) / 5f;
            canvas.drawRoundRect(barLeft, centerY - barHeight / 2f, filledRight,
                    centerY + barHeight / 2f, barHeight / 2f, barHeight / 2f, paint);

            paint.setColor(Color.rgb(27, 43, 31));
            paint.setTextSize(23f);
            paint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format(Locale.CHINA, "%.1f", avg), width - rightPadding, centerY + 8f, paint);
        }
        paint.setTextAlign(Paint.Align.LEFT);
    }
}
