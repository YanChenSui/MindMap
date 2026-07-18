package com.example.mindmap.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;

import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;

/** Small custom map marker icons used for ordered manual marks. */
public final class MapMarkerIconUtils {
    private static final int ORANGE = 0xffffa340;
    private static final int ORANGE_DARK = 0xffb86216;

    private MapMarkerIconUtils() {
    }

    public static BitmapDescriptor numberedManualMark(Context context, int order) {
        int width = dp(context, 38);
        int height = dp(context, 48);
        int circleRadius = dp(context, 16);
        int centerX = width / 2;
        int centerY = dp(context, 17);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(ORANGE);
        fill.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, circleRadius, fill);

        Path pointer = new Path();
        pointer.moveTo(centerX - dp(context, 8), centerY + dp(context, 11));
        pointer.lineTo(centerX + dp(context, 8), centerY + dp(context, 11));
        pointer.lineTo(centerX, height - dp(context, 4));
        pointer.close();
        canvas.drawPath(pointer, fill);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(ORANGE_DARK);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(context, 2));
        canvas.drawCircle(centerX, centerY, circleRadius, stroke);
        canvas.drawPath(pointer, stroke);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(order >= 100 ? dp(context, 11) : dp(context, 13));
        String label = String.valueOf(order);
        Rect bounds = new Rect();
        textPaint.getTextBounds(label, 0, label.length(), bounds);
        canvas.drawText(label, centerX, centerY + bounds.height() / 2f, textPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
