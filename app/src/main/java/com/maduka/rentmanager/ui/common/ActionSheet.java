package com.maduka.rentmanager.ui.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.maduka.rentmanager.R;

/** Bottom sheet of tappable action rows (icon tile + label + chevron) used when a Super Admin
 * taps a shop or tenant card. A "danger" action is red and always sits visually apart, last.
 * Build with {@code new ActionSheet(context, title, subtitle).add(...).danger(...).show()}. */
public final class ActionSheet {
    private final Context context;
    private final BottomSheetDialog dialog;
    private final LinearLayout actions;

    public ActionSheet(Context context, CharSequence title, CharSequence subtitle) {
        this.context = context;
        dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.sheet_actions, null, false);
        ((TextView) root.findViewById(R.id.sheetTitle)).setText(title);
        TextView sub = root.findViewById(R.id.sheetSubtitle);
        if (subtitle == null || subtitle.length() == 0) {
            sub.setVisibility(View.GONE);
        } else {
            sub.setText(subtitle);
        }
        actions = root.findViewById(R.id.sheetActions);
        root.findViewById(R.id.sheetClose).setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(root);
        // Let our own rounded sheet background show instead of the default flat container.
        View container = (View) root.getParent();
        if (container != null) container.setBackgroundColor(Color.TRANSPARENT);
    }

    public ActionSheet add(@DrawableRes int icon, @StringRes int label, Runnable onClick) {
        return addRow(icon, context.getString(label), null, false, onClick);
    }

    public ActionSheet add(@DrawableRes int icon, @StringRes int label, CharSequence hint, Runnable onClick) {
        return addRow(icon, context.getString(label), hint, false, onClick);
    }

    /** Destructive action: red row, red icon tile. */
    public ActionSheet danger(@DrawableRes int icon, @StringRes int label, Runnable onClick) {
        return addRow(icon, context.getString(label), null, true, onClick);
    }

    private ActionSheet addRow(int icon, String label, CharSequence hint, boolean danger, Runnable onClick) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_sheet_action, actions, false);
        ImageView iconView = row.findViewById(R.id.actionIcon);
        TextView labelView = row.findViewById(R.id.actionLabel);
        TextView hintView = row.findViewById(R.id.actionHint);
        FrameLayout tile = row.findViewById(R.id.actionIconTile);
        ImageView chevron = row.findViewById(R.id.actionChevron);

        iconView.setImageResource(icon);
        labelView.setText(label);
        if (hint != null && hint.length() > 0) {
            hintView.setText(hint);
            hintView.setVisibility(View.VISIBLE);
        }
        int fg = ContextCompat.getColor(context, danger ? R.color.md_status_bad_fg : R.color.md_accent_300);
        iconView.setImageTintList(ColorStateList.valueOf(fg));
        chevron.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context,
                danger ? R.color.md_status_bad_border : R.color.md_neutral_600)));
        if (danger) {
            row.setBackgroundResource(R.drawable.bg_action_row_danger);
            tile.setBackgroundResource(R.drawable.bg_icon_tile_danger);
            labelView.setTextColor(ContextCompat.getColor(context, R.color.md_status_bad_fg));
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) row.getLayoutParams();
            lp.topMargin = Math.round(context.getResources().getDisplayMetrics().density * 8);
        }
        row.setOnClickListener(v -> {
            dialog.dismiss();
            onClick.run();
        });
        actions.addView(row);
        return this;
    }

    public void show() {
        dialog.show();
    }
}
