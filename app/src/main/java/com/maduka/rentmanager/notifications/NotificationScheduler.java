package com.maduka.rentmanager.notifications;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** Schedules OverdueCheckReceiver to fire twice a day (09:00 and 18:00 device-local time), using
 * a plain inexact AlarmManager.set() re-armed from inside the receiver itself after each fire -
 * deliberately not setExactAndAllowWhileIdle, which would need the user to grant the
 * SCHEDULE_EXACT_ALARM special permission on API 31+ for something that doesn't need
 * second-level precision. */
public final class NotificationScheduler {
    private static final int[] HOURS = {9, 18};
    private NotificationScheduler() {}

    public static void ensureScheduled(Context context) {
        scheduleNext(context);
    }

    public static void scheduleNext(Context context) {
        long now = System.currentTimeMillis();
        long best = Long.MAX_VALUE;
        for (int hour : HOURS) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
            best = Math.min(best, c.getTimeInMillis());
        }

        Intent intent = new Intent(context, OverdueCheckReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, best, pendingIntent);
        }
    }
}
