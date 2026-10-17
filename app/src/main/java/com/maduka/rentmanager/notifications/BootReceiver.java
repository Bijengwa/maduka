package com.maduka.rentmanager.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** AlarmManager alarms are wiped on reboot; re-arm the twice-daily rent check so reminders
 * keep arriving without the user having to open the app first. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            NotificationScheduler.scheduleNext(context);
        }
    }
}
