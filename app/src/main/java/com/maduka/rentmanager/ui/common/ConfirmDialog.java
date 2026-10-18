package com.maduka.rentmanager.ui.common;

import android.content.Context;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.maduka.rentmanager.R;

/** Confirmation dialog for destructive actions: the confirm button is red so it reads as
 * dangerous, while Cancel stays in the normal accent colour. */
public final class ConfirmDialog {
    private ConfirmDialog() {}

    public static void danger(Context context, CharSequence title, CharSequence message,
                              int confirmLabelRes, Runnable onConfirm) {
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(confirmLabelRes, (d, w) -> onConfirm.run())
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(ContextCompat.getColor(context, R.color.md_status_bad_fg)));
        dialog.show();
    }
}
