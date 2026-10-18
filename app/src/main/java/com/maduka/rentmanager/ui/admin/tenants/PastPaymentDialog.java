package com.maduka.rentmanager.ui.admin.tenants;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.MadukaToast;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.Calendar;
import java.util.TimeZone;

/** Super-Admin dialog to record a payment a tenant already made earlier (amount, date, number it
 * was sent to - e.g. copied from an M-Pesa message). It gives the tenant a real Start date and
 * payment history and sets the rent end date to payment date + the months the amount covers. */
public final class PastPaymentDialog {
    private PastPaymentDialog() {}

    public static void show(Activity activity, PaymentRepository repository, Tenant tenant) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_past_payment, null, false);
        TextView tvIntro = view.findViewById(R.id.tvIntro);
        TextInputEditText etAmount = view.findViewById(R.id.etAmount);
        TextInputEditText etDate = view.findViewById(R.id.etDate);
        TextInputEditText etPhone = view.findViewById(R.id.etPhone);
        TextView tvSummary = view.findViewById(R.id.tvSummary);

        long rent = tenant.getMonthlyRent();
        tvIntro.setText(activity.getString(R.string.past_payment_intro_format, tenant.getName(),
                String.format(java.util.Locale.US, "%,d", rent)));

        long[] paymentDate = {System.currentTimeMillis()};
        etDate.setText(DateCalculator.formatDdMmYyyy(paymentDate[0]));

        Runnable refresh = () -> {
            long amount = parse(etAmount);
            int months = months(amount, rent);
            if (months > 0) {
                tvSummary.setText(activity.getString(R.string.tenants_existing_summary_format, months,
                        DateCalculator.formatDdMmYyyy(DateCalculator.addMonths(paymentDate[0], months))));
            } else {
                tvSummary.setText("");
            }
        };
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        etDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(paymentDate[0]);
            new DatePickerDialog(activity, (picker, y, m, d) -> {
                Calendar picked = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                picked.clear();
                picked.set(y, m, d, 0, 0, 0);
                paymentDate[0] = picked.getTimeInMillis();
                etDate.setText(DateCalculator.formatDdMmYyyy(paymentDate[0]));
                refresh.run();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
        });

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.past_payment_title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();
        // Validate before closing: override the positive button so bad input keeps the dialog open.
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long amount = parse(etAmount);
            String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
            int months = months(amount, rent);
            if (months <= 0) {
                MadukaToast.show(activity, activity.getString(R.string.past_payment_error_amount), MadukaToast.Kind.BAD);
                return;
            }
            if (phone.isEmpty()) {
                MadukaToast.show(activity, activity.getString(R.string.payments_error_phone_required), MadukaToast.Kind.BAD);
                return;
            }
            long due = DateCalculator.addMonths(paymentDate[0], months);
            dialog.dismiss();
            repository.recordPastPayment(tenant, amount, months, paymentDate[0], phone, due,
                    new AuthRepository().currentUid(), new FirebaseManager.Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            if (activity.isFinishing()) return;
                            MadukaToast.show(activity, activity.getString(R.string.past_payment_success_format,
                                    tenant.getName(), DateCalculator.formatDdMmYyyy(due)), MadukaToast.Kind.OK);
                        }

                        @Override
                        public void onError(String message) {
                            if (activity.isFinishing()) return;
                            MadukaToast.show(activity, message, MadukaToast.Kind.BAD);
                        }
                    });
        }));
        dialog.show();
    }

    private static long parse(TextInputEditText et) {
        String t = et.getText() != null ? et.getText().toString().replace(",", "").trim() : "";
        if (t.isEmpty()) return 0;
        try {
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int months(long amount, long rent) {
        return (amount > 0 && rent > 0) ? Math.max(1, Math.round(amount / (float) rent)) : 0;
    }
}
