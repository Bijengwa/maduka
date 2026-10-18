package com.maduka.rentmanager.ui.admin.tenants;

import android.app.Activity;
import android.app.DatePickerDialog;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.MadukaToast;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.Calendar;
import java.util.TimeZone;

/** Super-Admin "this tenant's rent runs out on <date>" picker, shared by the Tenants list and
 * the Shops list so both write the same tenants/{uid}/dueDate via TenantRepository.setDueDate. */
public final class DueDatePicker {
    private DueDatePicker() {}

    public static void show(Activity activity, TenantRepository repository, Tenant tenant) {
        long initial = DateCalculator.hasValidDueDate(tenant.getDueDate())
                ? tenant.getDueDate() : DateCalculator.addMonths(System.currentTimeMillis(), 1);
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(initial);
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            // Stored as midnight UTC, the convention DateCalculator formats and compares with.
            Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            picked.clear();
            picked.set(year, month, day, 0, 0, 0);
            long dueDate = picked.getTimeInMillis();
            repository.setDueDate(tenant.getUid(), dueDate, new FirebaseManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    if (activity.isFinishing()) return;
                    MadukaToast.show(activity, activity.getString(R.string.tenants_set_due_date_success,
                            tenant.getName(), DateCalculator.formatDdMmYyyy(dueDate)), MadukaToast.Kind.OK);
                }

                @Override
                public void onError(String message) {
                    if (activity.isFinishing()) return;
                    MadukaToast.show(activity, message, MadukaToast.Kind.BAD);
                }
            });
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }
}
