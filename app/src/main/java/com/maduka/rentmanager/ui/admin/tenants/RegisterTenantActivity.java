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
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
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

        paymentDateMillis = System.currentTimeMillis();
        etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
        etPaymentDate.setOnClickListener(v -> showDatePicker());

        etMonthsPaid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateComputedAmount(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        spinnerShop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updateComputedAmount(); }
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
                Toast.makeText(RegisterTenantActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });

        btnRegister.setOnClickListener(v -> submit());
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
        btnRegister.setEnabled(enabled);
    }

    private void showDatePicker() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(paymentDateMillis);
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar picked = Calendar.getInstance();
            picked.set(year, month, dayOfMonth, 0, 0, 0);
            picked.set(Calendar.MILLISECOND, 0);
            paymentDateMillis = picked.getTimeInMillis();
            etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateComputedAmount() {
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
            Toast.makeText(this, R.string.users_error_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, R.string.change_password_error_length, Toast.LENGTH_SHORT).show();
            return;
        }

        int months = parseMonths();
        if (months <= 0) {
            Toast.makeText(this, R.string.tenants_error_invalid_months, Toast.LENGTH_SHORT).show();
            return;
        }

        String paymentPhone = textOf(etPaymentPhone);
        if (paymentPhone.isEmpty()) {
            Toast.makeText(this, R.string.payments_error_phone_required, Toast.LENGTH_SHORT).show();
            return;
        }

        int selected = spinnerShop.getSelectedItemPosition();
        if (selected < 0 || selected >= vacantShops.size()) return;
        Shop selectedShop = vacantShops.get(selected);

        String recordedByUid = new AuthRepository().currentUid();

        setFormEnabled(false);
        tenantRepository.registerTenant(name, phone, email, password, selectedShop, months, paymentDateMillis,
                paymentPhone, recordedByUid, new FirebaseManager.Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                finish();
            }

            @Override
            public void onError(String message) {
                setFormEnabled(true);
                Toast.makeText(RegisterTenantActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
