package com.example.mindmap.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import com.example.mindmap.R;
import com.google.android.material.button.MaterialButton;

/** 统一页面字号、边距、卡片圆角和按钮样式，避免各页面视觉不一致。 */
public final class UiFactory {
    public static final int PAGE_HORIZONTAL_PADDING_DP = 16;
    public static final int CARD_RADIUS_DP = 12;

    private UiFactory() {}

    public static TextView title(Context context, String value) {
        return text(context, value, 30, Typeface.BOLD);
    }

    public static TextView sectionTitle(Context context, String value) {
        return text(context, value, 22, Typeface.BOLD);
    }

    public static TextView cardTitle(Context context, String value) {
        return text(context, value, 18, Typeface.BOLD);
    }

    public static TextView body(Context context, String value) {
        return text(context, value, 15, Typeface.NORMAL);
    }

    public static TextView mutedText(Context context, String value) {
        TextView view = text(context, value, 14, Typeface.NORMAL);
        view.setTextColor(0xff5c6f61);
        return view;
    }

    public static TextView text(Context context, String value, float sp, int style) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(context.getColor(R.color.earth_text));
        view.setTypeface(Typeface.DEFAULT, style);
        view.setIncludeFontPadding(true);
        view.setPadding(0, dp(context, 3), 0, dp(context, 3));
        return view;
    }

    public static CardView card(Context context) {
        CardView card = new CardView(context);
        card.setRadius(dp(context, CARD_RADIUS_DP));
        card.setCardElevation(dp(context, 1));
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(0xffffffff);
        card.setContentPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(context, 6), 0, dp(context, 6));
        card.setLayoutParams(params);
        return card;
    }

    public static CardView stateCard(Context context, String title, String message) {
        CardView card = card(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setPadding(0, dp(context, 10), 0, dp(context, 10));
        TextView titleView = cardTitle(context, title);
        titleView.setGravity(Gravity.CENTER);
        TextView messageView = mutedText(context, message);
        messageView.setGravity(Gravity.CENTER);
        body.addView(titleView);
        body.addView(messageView);
        card.addView(body);
        return card;
    }

    public static MaterialButton primaryButton(Context context, String text) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setCornerRadius(dp(context, 12));
        button.setBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.park_green)));
        return button;
    }

    public static MaterialButton secondaryButton(Context context, String text) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setCornerRadius(dp(context, 12));
        button.setStrokeWidth(dp(context, 1));
        button.setStrokeColor(ColorStateList.valueOf(context.getColor(R.color.park_green)));
        button.setTextColor(context.getColor(R.color.park_green));
        button.setBackgroundTintList(ColorStateList.valueOf(0xffffffff));
        return button;
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static View mapLegend(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(context, 3), 0, 0);
        addLegendItem(row, 0xff2e7d32, "起点");
        addLegendItem(row, 0xffd32f2f, "终点");
        addLegendItem(row, 0xffffa340, "手动标记");
        return row;
    }

    private static void addLegendItem(LinearLayout row, int color, String label) {
        TextView dot = new TextView(row.getContext());
        dot.setText("●");
        dot.setTextSize(12);
        dot.setTextColor(color);
        row.addView(dot);

        TextView text = text(row.getContext(), label, 12, Typeface.NORMAL);
        text.setTextColor(0xff5c6f61);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(row.getContext(), 3), 0, dp(row.getContext(), 10), 0);
        row.addView(text, params);
    }
}
