package com.maduka.rentmanager.util;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PresenceStatus;

/** Maps the app's business statuses onto the shared 4-color status system. */
public final class StatusPresentation {
    private StatusPresentation() {}

    public enum Tone { GOOD, WAIT, BAD, EMPTY }

    /** ACTIVE/Hai -> green, NEXT_DUE/Inakaribia -> amber, OVERDUE/Imepita muda -> red,
     * UNKNOWN (no dueDate on record) -> the same neutral tone as an empty/vacant shop. */
    public static Tone toneFor(DateCalculator.DueBucket bucket) {
        switch (bucket) {
            case ACTIVE: return Tone.GOOD;
            case NEXT_DUE: return Tone.WAIT;
            case OVERDUE: return Tone.BAD;
            default: return Tone.EMPTY;
        }
    }

    public static Tone toneFor(PresenceStatus status) {
        return status == PresenceStatus.YUPO ? Tone.GOOD : Tone.BAD;
    }

    public static int fgColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_fg;
            case WAIT: return R.color.md_status_wait_fg;
            case BAD: return R.color.md_status_bad_fg;
            default: return R.color.md_status_empty_fg;
        }
    }

    public static int bgColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_bg;
            case WAIT: return R.color.md_status_wait_bg;
            case BAD: return R.color.md_status_bad_bg;
            default: return android.R.color.transparent;
        }
    }

    public static int borderColorRes(Tone tone) {
        switch (tone) {
            case GOOD: return R.color.md_status_good_border;
            case WAIT: return R.color.md_status_wait_border;
            case BAD: return R.color.md_status_bad_border;
            default: return R.color.md_status_empty_fg;
        }
    }
}
