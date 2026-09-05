package com.maduka.rentmanager.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class DateCalculator {
    private DateCalculator() {}

    public static long addMonths(long epochMillis, int months) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(epochMillis);
        c.add(Calendar.MONTH, months);
        return c.getTimeInMillis();
    }

    /** Whole days between two instants; positive when `to` is after `from`. */
    public static int daysBetween(long fromMillis, long toMillis) {
        return (int) TimeUnit.MILLISECONDS.toDays(toMillis - fromMillis);
    }

    public static boolean isOverdue(long dueDateMillis, long nowMillis) {
        return dueDateMillis < nowMillis;
    }

    public static String formatDdMmYyyy(long millis) {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(millis));
    }
}
