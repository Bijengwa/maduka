package com.maduka.rentmanager.ui.common;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.util.StatusPresentation;

/** Shared status-pill/accent-bar rendering, extracted from ShopAdapter/TenantAdapter so every
 * screen's status badge (shop occupancy, tenant presence, payment status, notification type)
 * looks and behaves identically. */
public final class StatusPill {
    private StatusPill() {}

    public static void apply(TextView view, StatusPresentation.Tone tone, String label) {
        view.setText(label);
        Context context = view.getContext();
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(context.getResources().getDimension(R.dimen.radius_full));
        bg.setColor(ContextCompat.getColor(context, StatusPresentation.bgColorRes(tone)));
        int strokeWidth = Math.round(context.getResources().getDisplayMetrics().density);
        bg.setStroke(strokeWidth, ContextCompat.getColor(context, StatusPresentation.borderColorRes(tone)));
        view.setBackground(bg);
        view.setTextColor(ContextCompat.getColor(context, StatusPresentation.fgColorRes(tone)));
    }

    public static void apply(TextView view, StatusPresentation.Tone tone, int labelRes) {
        apply(view, tone, view.getContext().getString(labelRes));
    }

    public static void accent(View accentBar, StatusPresentation.Tone tone) {
        accentBar.setBackgroundColor(ContextCompat.getColor(accentBar.getContext(), StatusPresentation.borderColorRes(tone)));
    }
}
