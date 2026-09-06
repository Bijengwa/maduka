package com.maduka.rentmanager.util;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Helper for applying system-bar WindowInsets ("SafeAreaInsets" equivalent)
 * as EXTRA padding on top of a view's existing (XML-declared) padding.
 *
 * The view's original padding is captured ONCE, before the listener is
 * attached, and every insets callback (e.g. on rotation, when the listener
 * can fire again) re-applies the inset on top of that same fixed baseline —
 * never on top of whatever padding the previous callback left behind. This
 * avoids the classic bug where padding keeps growing every time insets are
 * recalculated.
 */
public final class EdgeToEdge {
    private EdgeToEdge() {}

    private interface PaddingApplier {
        void apply(View view, int initialLeft, int initialTop, int initialRight, int initialBottom, Insets bars);
    }

    private static void applyInsets(View view, PaddingApplier applier) {
        final int initialLeft = view.getPaddingLeft();
        final int initialTop = view.getPaddingTop();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            applier.apply(v, initialLeft, initialTop, initialRight, initialBottom, bars);
            return insets;
        });
    }

    /** Adds the top system-bar inset on top of the view's original top padding. */
    public static void applyTopInset(View view) {
        applyInsets(view, (v, left, top, right, bottom, bars) ->
                v.setPadding(left, top + bars.top, right, bottom));
    }

    /** Adds the bottom system-bar inset on top of the view's original bottom padding. */
    public static void applyBottomInset(View view) {
        applyInsets(view, (v, left, top, right, bottom, bars) ->
                v.setPadding(left, top, right, bottom + bars.bottom));
    }
}
