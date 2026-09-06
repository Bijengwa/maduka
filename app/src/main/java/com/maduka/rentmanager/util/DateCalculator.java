package com.maduka.rentmanager.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class DateCalculator {
    private DateCalculator() {}

    /** How a tenant's current dueDate relates to today, using calendar-day boundaries (not a
     * crude 30*24h block) - the basis for the dashboard's Zote/Inakaribia/Hai/Imechelewa filters.
     * UNKNOWN means the record has no usable dueDate (missing/zero/negative) - see
     * {@link #hasValidDueDate}. */
    public enum DueBucket { OVERDUE, NEXT_DUE, ACTIVE, UNKNOWN }

    private static final int NEXT_DUE_WINDOW_DAYS = 30;

    public static long addMonths(long epochMillis, int months) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epochMillis);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    public static long addDays(long epochMillis, int days) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epochMillis);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTimeInMillis();
    }

    /** Midnight UTC of the calendar day containing the given instant. */
    public static long startOfDay(long epochMillis) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epochMillis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** A tenant/shop record with no dueDate ever written (Firebase default 0 = the Unix epoch)
     * is not "overdue by tens of thousands of days" - it simply has no due date to compare
     * against. Every overdue/next-due computation must check this first instead of treating 0
     * as a real date. */
    public static boolean hasValidDueDate(long dueDateMillis) {
        return dueDateMillis > 0;
    }

    /** Whole days between two instants; positive when `to` is after `from`. */
    public static int daysBetween(long fromMillis, long toMillis) {
        return (int) TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis);
    }

    public static boolean isOverdue(long dueDateMillis, long nowMillis) {
        return hasValidDueDate(dueDateMillis) && dueDateMillis < nowMillis;
    }

    /** Calendar-correct classification for the dashboard's payment filters: OVERDUE (dueDate
     * before today), NEXT_DUE (today through today+30 calendar days inclusive), ACTIVE (further
     * out), or UNKNOWN when there is no usable dueDate at all. */
    public static DueBucket classifyDueDate(long dueDateMillis, long nowMillis) {
        if (!hasValidDueDate(dueDateMillis)) return DueBucket.UNKNOWN;
        long today = startOfDay(nowMillis);
        long due = startOfDay(dueDateMillis);
        if (due < today) return DueBucket.OVERDUE;
        if (due <= addDays(today, NEXT_DUE_WINDOW_DAYS)) return DueBucket.NEXT_DUE;
        return DueBucket.ACTIVE;
    }

    public static String formatDdMmYyyy(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(millis));
    }

    /** Same as {@link #formatDdMmYyyy} but never renders the Unix epoch for a dueDate that was
     * simply never set - callers should show unknownLabel (e.g. "Due date not set") instead. */
    public static String formatDueDateOrUnknown(long dueDateMillis, String unknownLabel) {
        return hasValidDueDate(dueDateMillis) ? formatDdMmYyyy(dueDateMillis) : unknownLabel;
    }
}
