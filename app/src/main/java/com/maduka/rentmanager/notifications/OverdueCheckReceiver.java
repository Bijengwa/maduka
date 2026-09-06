package com.maduka.rentmanager.notifications;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.List;
import java.util.Locale;

/** Fires twice a day (NotificationScheduler) to post a local overdue/due-soon reminder for
 * whichever role is currently signed in on this device. Purely local and read-only: one-shot
 * repository reads only (goAsync + addListenerForSingleValueEvent, never a live listener left
 * running after onReceive returns), and it never writes anything to Firebase - it only decides,
 * from the signed-in uid, whether this device belongs to a tenant (their own due state) or an
 * admin/super admin (a portfolio-wide overdue count), then posts one system notification. */
public class OverdueCheckReceiver extends BroadcastReceiver {
    private static final int NOTIFICATION_ID_TENANT = 1001;
    private static final int NOTIFICATION_ID_ADMIN_SUMMARY = 1002;
    private static final int DUE_SOON_WINDOW_DAYS = 7;

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationScheduler.scheduleNext(context);

        String uid = new AuthRepository().currentUid();
        if (uid == null) return;

        PendingResult pendingResult = goAsync();
        TenantRepository tenantRepository = new TenantRepository();
        Context appContext = context.getApplicationContext();

        tenantRepository.observeTenantOnce(uid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (tenant != null) {
                    notifyTenant(appContext, tenant);
                    pendingResult.finish();
                } else {
                    checkAdminSummary(appContext, tenantRepository, pendingResult);
                }
            }

            @Override
            public void onError(String message) {
                pendingResult.finish();
            }
        });
    }

    private void checkAdminSummary(Context context, TenantRepository tenantRepository, PendingResult pendingResult) {
        tenantRepository.observeTenantsOnce(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                long now = System.currentTimeMillis();
                int overdueCount = 0;
                for (Tenant t : tenants) {
                    if (t != null && DateCalculator.isOverdue(t.getDueDate(), now)) overdueCount++;
                }
                if (overdueCount > 0) {
                    String title = context.getString(R.string.notif_admin_summary_title);
                    String message = context.getString(R.string.notif_admin_summary_message_format, overdueCount);
                    NotificationHelper.show(context, NOTIFICATION_ID_ADMIN_SUMMARY, title, message);
                }
                pendingResult.finish();
            }

            @Override
            public void onError(String message) {
                pendingResult.finish();
            }
        });
    }

    private void notifyTenant(Context context, Tenant tenant) {
        long now = System.currentTimeMillis();
        int days = DateCalculator.daysBetween(now, tenant.getDueDate());
        String rent = String.format(Locale.US, "%,d", tenant.getMonthlyRent());

        String title;
        String message;
        if (days < 0) {
            title = context.getString(R.string.notif_system_overdue_title);
            message = context.getString(R.string.notif_system_overdue_message_format, rent, Math.abs(days));
        } else if (days == 0) {
            title = context.getString(R.string.notif_system_due_today_title);
            message = context.getString(R.string.notif_system_due_today_message_format, rent);
        } else if (days <= DUE_SOON_WINDOW_DAYS) {
            title = context.getString(R.string.notif_system_due_soon_title);
            message = context.getString(R.string.notif_system_due_soon_message_format, rent, days);
        } else {
            return;
        }
        NotificationHelper.show(context, NOTIFICATION_ID_TENANT, title, message);
    }
}
