package com.maduka.rentmanager.ui.common;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.maduka.rentmanager.R;

/** Reference-matched toast: a rounded, icon+message card that slides up from the bottom and
 * auto-dismisses, replacing the system Toast everywhere. Sits above the bottom nav bar when one
 * is present (MainActivity's hosted fragments), or just above the screen edge otherwise
 * (standalone form Activities). */
public final class MadukaToast {
    private static final long DURATION_MS = 3600;

    public enum Kind { OK, INFO, BAD }

    private MadukaToast() {}

    public static void show(Activity activity, String message) {
        show(activity, message, Kind.INFO);
    }

    public static void show(Activity activity, String message, Kind kind) {
        if (activity == null || activity.isFinishing() || message == null) return;
        ViewGroup decor = activity.findViewById(android.R.id.content);
        if (decor == null) return;

        View toast = LayoutInflater.from(activity).inflate(R.layout.view_maduka_toast, decor, false);
        View root = toast.findViewById(R.id.toastRoot);
        ImageView icon = toast.findViewById(R.id.ivToastIcon);
        TextView text = toast.findViewById(R.id.tvToastMessage);
        text.setText(message);

        switch (kind) {
            case OK:
                root.setBackgroundResource(R.drawable.bg_button_ok);
                icon.setImageResource(R.drawable.ic_ph_check_circle);
                icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.md_status_good_fg));
                break;
            case BAD:
                root.setBackgroundResource(R.drawable.bg_button_danger);
                icon.setImageResource(R.drawable.ic_ph_warning_circle);
                icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.md_status_bad_fg));
                break;
            default:
                root.setBackgroundResource(R.drawable.bg_toast_info);
                icon.setImageResource(R.drawable.ic_ph_info);
                icon.setImageTintList(ContextCompat.getColorStateList(activity, R.color.md_accent_300));
        }

        View bottomNav = activity.findViewById(R.id.bottomNav);
        int bottomMarginDp = (bottomNav != null && bottomNav.getVisibility() == View.VISIBLE) ? 78 : 16;
        float density = activity.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.BOTTOM;
        int sideMargin = Math.round(16 * density);
        params.setMargins(sideMargin, 0, sideMargin, Math.round(bottomMarginDp * density));
        decor.addView(toast, params);

        toast.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.maduka_toast_up));

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (toast.getParent() == null) return;
            decor.removeView(toast);
        }, DURATION_MS);
    }
}
