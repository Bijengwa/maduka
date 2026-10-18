package com.maduka.rentmanager.ui.admin.tenants;

import android.app.DatePickerDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import com.maduka.rentmanager.ui.common.MadukaToast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Full-screen registration for a new tenant, gated to shops that are currently vacant
 * (shop.isOccupied() == false) - a shop that already has a tenant cannot be selected until its
 * tenant relationship ends and it goes back to vacant. Account creation goes through
 * TenantRepository.registerTenant, which uses the same secondary-FirebaseAuth pattern as
 * UserRepository.registerAdmin, so the currently signed-in Admin/Super Admin's own session is
 * never disturbed.
 *
 * Super-Admin-only: TenantsFragment only wires the launching click listener for that role;
 * this Activity also refuses to open for any other role passed via EXTRA_ROLE, so the
 * restriction lives in the code path itself, not just a hidden button. */
public class RegisterTenantActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";
    /** Optional: preselects this (vacant) shop in the picker, e.g. when launched from a shop card. */
    public static final String EXTRA_SHOP_ID = "extra_shop_id";
    /** Optional: start in "has not paid yet - set the next payment date" mode, for tenants who
     * already exist and are simply being entered into the system. */
    public static final String EXTRA_SCHEDULE_ONLY = "extra_schedule_only";

    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();

    private final List<Shop> vacantShops = new ArrayList<>();

    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Spinner spinnerShop;
    private TextInputEditText etMonthsPaid;
    private TextInputEditText etPaymentDate;
    private TextInputEditText etPaymentPhone;
    private TextView tvComputedAmount;
    private TextView tvNoVacantShops;
    private Button btnRegister;
    private CheckBox cbNotPaidYet, cbPresent;
    private View tilAmountPaid, tilNextDate, tvNextDateLabel;
    private TextInputEditText etAmountPaid, etNextDate;
    private long nextDateMillis;
    /** Once the Super Admin picks the next payment date by hand, stop auto-deriving it. */
    private boolean nextDateManual;
    private View tilMonthsPaid, tilPaymentPhone;
    private TextView tvPaymentDateLabel;
    private long paymentDateMillis;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String roleName = getIntent().getStringExtra(EXTRA_ROLE);
        UserRole role = roleName != null ? UserRole.valueOf(roleName) : null;
        if (role != UserRole.SUPER_ADMIN) {
            finish();
            return;
        }

        setContentView(R.layout.activity_register_tenant);

        View root = findViewById(R.id.registerTenantRoot);
        View topBar = findViewById(R.id.registerTenantTopBar);
        EdgeToEdge.applyTopInset(topBar);
        EdgeToEdge.applyBottomInset(root);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        spinnerShop = findViewById(R.id.spinnerShop);
        etMonthsPaid = findViewById(R.id.etMonthsPaid);
        etPaymentDate = findViewById(R.id.etPaymentDate);
        etPaymentPhone = findViewById(R.id.etPaymentPhone);
        tvComputedAmount = findViewById(R.id.tvComputedAmount);
        tvNoVacantShops = findViewById(R.id.tvNoVacantShops);
        btnRegister = findViewById(R.id.btnRegister);
        cbNotPaidYet = findViewById(R.id.cbNotPaidYet);
        cbPresent = findViewById(R.id.cbPresent);
        tilAmountPaid = findViewById(R.id.tilAmountPaid);
        tilNextDate = findViewById(R.id.tilNextDate);
        tvNextDateLabel = findViewById(R.id.tvNextDateLabel);
        etAmountPaid = findViewById(R.id.etAmountPaid);
        etNextDate = findViewById(R.id.etNextDate);
        etNextDate.setOnClickListener(v -> showNextDatePicker());
        TextWatcher recompute = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateExistingSummary(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etAmountPaid.addTextChangedListener(recompute);
        tilMonthsPaid = findViewById(R.id.tilMonthsPaid);
        tilPaymentPhone = findViewById(R.id.tilPaymentPhone);
        tvPaymentDateLabel = findViewById(R.id.tvPaymentDateLabel);
        cbNotPaidYet.setOnCheckedChangeListener((b, checked) -> applyMode(checked));

        paymentDateMillis = System.currentTimeMillis();
        etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
        etPaymentDate.setOnClickListener(v -> showDatePicker());

        etMonthsPaid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateComputedAmount(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        spinnerShop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateComputedAmount(); updateExistingSummary(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Disabled until the one-time vacant-shop read below comes back, so the Super
        // Admin/Admin can never submit before we know whether there's anywhere to put a tenant.
        setFormEnabled(false);

        // A single read (not a live subscription) is enough here - the picker reflects
        // vacancy at the moment this screen opened, and a single read auto-detaches so there
        // is no dangling listener to clean up after the Activity is destroyed.
        shopRepository.observeShopsOnce(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                vacantShops.clear();
                for (Shop shop : shops) {
                    if (!shop.isOccupied()) vacantShops.add(shop);
                }
                bindVacantShops();
            }

            @Override
            public void onError(String message) {
                MadukaToast.show(RegisterTenantActivity.this, message, MadukaToast.Kind.BAD);
            }
        });

        btnRegister.setOnClickListener(v -> submit());
        if (getIntent().getBooleanExtra(EXTRA_SCHEDULE_ONLY, false)) {
            cbNotPaidYet.setChecked(true);
        }
    }

    /** "Existing tenant" mode: someone who already rents here and is being entered into the
     * system. The Super Admin gives what they know - the last amount paid and when (optional, e.g.
     * from an M-Pesa message), when the rent runs out, and whether the tenant is there now. Hides
     * the new-payment months field. */
    private void applyMode(boolean existing) {
        tilMonthsPaid.setVisibility(existing ? View.GONE : View.VISIBLE);
        tilAmountPaid.setVisibility(existing ? View.VISIBLE : View.GONE);
        tvNextDateLabel.setVisibility(existing ? View.VISIBLE : View.GONE);
        tilNextDate.setVisibility(existing ? View.VISIBLE : View.GONE);
        cbPresent.setVisibility(existing ? View.VISIBLE : View.GONE);
        tvPaymentDateLabel.setText(existing ? R.string.tenants_last_payment_date_label
                : R.string.tenants_payment_date_label);
        etPaymentPhone.setHint(existing ? R.string.tenants_paid_to_optional_hint : R.string.payments_phone_hint);
        paymentDateMillis = System.currentTimeMillis();
        etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
        nextDateManual = false;
        updateExistingSummary();
        updateComputedAmount();
    }

    /** Existing mode: derive months and the next payment date from the amount and last payment
     * date (rent per month of the selected shop) until the Super Admin sets the date by hand. */
    private void updateExistingSummary() {
        if (!cbNotPaidYet.isChecked()) return;
        long rent = selectedRent();
        long amount = parseAmount();
        int months = (amount > 0 && rent > 0) ? Math.max(1, Math.round(amount / (float) rent)) : 0;
        if (!nextDateManual) {
            nextDateMillis = months > 0
                    ? DateCalculator.addMonths(paymentDateMillis, months)
                    : DateCalculator.addMonths(System.currentTimeMillis(), 1);
            etNextDate.setText(DateCalculator.formatDdMmYyyy(nextDateMillis));
        }
        if (months > 0) {
            tvComputedAmount.setVisibility(View.VISIBLE);
            tvComputedAmount.setText(getString(R.string.tenants_existing_summary_format, months,
                    DateCalculator.formatDdMmYyyy(nextDateMillis)));
        } else {
            tvComputedAmount.setVisibility(View.GONE);
        }
    }

    private long selectedRent() {
        int selected = spinnerShop.getSelectedItemPosition();
        return (selected >= 0 && selected < vacantShops.size()) ? vacantShops.get(selected).getMonthlyRent() : 0;
    }

    private long parseAmount() {
        String text = textOf(etAmountPaid).replace(",", "");
        if (text.isEmpty()) return 0;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void showNextDatePicker() {
        Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(nextDateMillis);
        new DatePickerDialog(this, (view, year, month, day) -> {
            Calendar picked = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            picked.clear();
            picked.set(year, month, day, 0, 0, 0);
            nextDateMillis = picked.getTimeInMillis();
            nextDateManual = true;
            etNextDate.setText(DateCalculator.formatDdMmYyyy(nextDateMillis));
            updateExistingSummary();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void bindVacantShops() {
        boolean hasVacant = !vacantShops.isEmpty();
        tvNoVacantShops.setVisibility(hasVacant ? View.GONE : View.VISIBLE);
        setFormEnabled(hasVacant);

        if (hasVacant) {
            List<String> names = new ArrayList<>();
            for (Shop shop : vacantShops) {
                names.add(shop.getName() != null ? shop.getName() : shop.getShopId());
            }
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerShop.setAdapter(spinnerAdapter);
            String preselect = getIntent().getStringExtra(EXTRA_SHOP_ID);
            if (preselect != null) {
                for (int i = 0; i < vacantShops.size(); i++) {
                    if (preselect.equals(vacantShops.get(i).getShopId())) {
                        spinnerShop.setSelection(i);
                        break;
                    }
                }
            }
            updateComputedAmount();
        }
    }

    private void setFormEnabled(boolean enabled) {
        etName.setEnabled(enabled);
        etPhone.setEnabled(enabled);
        etEmail.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        spinnerShop.setEnabled(enabled);
        etMonthsPaid.setEnabled(enabled);
        etPaymentDate.setEnabled(enabled);
        etPaymentPhone.setEnabled(enabled);
        cbNotPaidYet.setEnabled(enabled);
        etAmountPaid.setEnabled(enabled);
        etNextDate.setEnabled(enabled);
        cbPresent.setEnabled(enabled);
        btnRegister.setEnabled(enabled);
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(paymentDateMillis);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
            picked.clear();
            picked.set(year, month, dayOfMonth, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);
            paymentDateMillis = picked.getTimeInMillis();
            etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
            updateExistingSummary();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateComputedAmount() {
        if (cbNotPaidYet.isChecked()) return; // existing mode shows its own summary
        int selected = spinnerShop.getSelectedItemPosition();
        if (selected < 0 || selected >= vacantShops.size()) {
            tvComputedAmount.setText("");
            return;
        }
        int months = parseMonths();
        if (months <= 0) {
            tvComputedAmount.setText("");
            return;
        }
        long rent = vacantShops.get(selected).getMonthlyRent();
        long amount = (long) months * rent;
        tvComputedAmount.setText(getString(R.string.properties_per_month_amount_format,
                String.format(Locale.US, "%,d", amount)));
    }

    private int parseMonths() {
        String text = textOf(etMonthsPaid);
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void submit() {
        if (vacantShops.isEmpty()) return;

        String name = textOf(etName);
        String phone = textOf(etPhone);
        String email = textOf(etEmail);
        String password = textOf(etPassword);

        if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
            MadukaToast.show(this, getString(R.string.users_error_required), MadukaToast.Kind.BAD);
            return;
        }
        if (password.length() < 6) {
            MadukaToast.show(this, getString(R.string.change_password_error_length), MadukaToast.Kind.BAD);
            return;
        }

        boolean existing = cbNotPaidYet.isChecked();
        int selected = spinnerShop.getSelectedItemPosition();
        if (selected < 0 || selected >= vacantShops.size()) return;
        Shop selectedShop = vacantShops.get(selected);
        String recordedByUid = new AuthRepository().currentUid();
        String paymentPhone = textOf(etPaymentPhone);

        FirebaseManager.Callback<Void> done = new FirebaseManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(String message) {
                setFormEnabled(true);
                MadukaToast.show(RegisterTenantActivity.this, message, MadukaToast.Kind.BAD);
            }
        };

        if (existing) {
            long amount = parseAmount();
            long rent = selectedShop.getMonthlyRent();
            int months = (amount > 0 && rent > 0) ? Math.max(1, Math.round(amount / (float) rent)) : 0;
            if (amount > 0 && paymentPhone.isEmpty()) {
                MadukaToast.show(this, getString(R.string.payments_error_phone_required), MadukaToast.Kind.BAD);
                return;
            }
            PresenceStatus presence = cbPresent.isChecked() ? PresenceStatus.YUPO : PresenceStatus.HAYUPO;
            setFormEnabled(false);
            tenantRepository.registerExistingTenant(name, phone, email, password, selectedShop, amount, months,
                    paymentDateMillis, paymentPhone, nextDateMillis, presence, recordedByUid, done);
            return;
        }

        int months = parseMonths();
        if (months <= 0) {
            MadukaToast.show(this, getString(R.string.tenants_error_invalid_months), MadukaToast.Kind.BAD);
            return;
        }
        if (paymentPhone.isEmpty()) {
            MadukaToast.show(this, getString(R.string.payments_error_phone_required), MadukaToast.Kind.BAD);
            return;
        }
        setFormEnabled(false);
        tenantRepository.registerTenant(name, phone, email, password, selectedShop, months, paymentDateMillis,
                paymentPhone, recordedByUid, done);
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
