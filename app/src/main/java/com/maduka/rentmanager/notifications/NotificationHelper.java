package com.maduka.rentmanager.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.maduka.rentmanager.R;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "overdue_reminders";
    private NotificationHelper() {}

    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Rent reminders", NotificationManager.IMPORTANCE_DEFAULT);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /** Posts a local notification, replacing any earlier one with the same notificationId
     * (stable per-tenant/per-summary id) rather than stacking a new one - this is what keeps a
     * 2x/day recurring reminder from spamming duplicates in the tray. Silently no-ops if
     * POST_NOTIFICATIONS isn't granted (API 33+) instead of crashing - a background receiver
     * must not assume the user has granted it. */
    public static void show(Context context, int notificationId, String title, String message) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_maduka_mark)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true);
        NotificationManagerCompat.from(context).notify(notificationId, builder.build());
    }
}
