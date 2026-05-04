package com.example.campusvista.ui.common;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.campusvista.R;

public final class ViewFactory {
    private ViewFactory() {
    }

    public static Button listButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(context.getResources().getColor(R.color.cv_navy));
        button.setTextSize(14f);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(3);
        button.setBackgroundResource(R.drawable.cv_button_secondary);
        button.setMinHeight(dp(context, 58));
        button.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 10));
        button.setElevation(dp(context, 1));
        button.setLayoutParams(blockParams(context, 10));
        return button;
    }

    public static Button chipButton(Context context, String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(context.getResources().getColor(R.color.cv_green_dark));
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundResource(R.drawable.cv_category_chip);
        button.setElevation(dp(context, 1));
        button.setMinWidth(dp(context, 86));
        button.setPadding(dp(context, 18), 0, dp(context, 18), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(context, 44)
        );
        params.setMargins(0, 0, dp(context, 10), 0);
        button.setLayoutParams(params);
        return button;
    }

    public static TextView sectionLine(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(context.getResources().getColor(R.color.cv_muted));
        view.setTextSize(14f);
        view.setPadding(0, dp(context, 8), 0, dp(context, 8));
        view.setLayoutParams(blockParams(context, 4));
        return view;
    }

    public static void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams blockParams(Context context, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(context, topMarginDp), 0, 0);
        return params;
    }
}
