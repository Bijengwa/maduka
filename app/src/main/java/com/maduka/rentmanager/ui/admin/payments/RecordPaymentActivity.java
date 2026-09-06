package com.maduka.rentmanager.ui.admin.payments;

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
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Records a rent renewal payment for an already-active tenant (Super Admin only - gated the
 * same way RegisterTenantActivity is: TenantsFragment/AdminDashboardFragment only ever launch
 * this for that role, and this Activity also refuses to open for any other role). There is no
 * approval workflow - PaymentRepository.recordPayment() makes the payment immediately valid and
 * rolls the tenant's dueDate forward. */
public class RecordPaymentActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";

    private static final String[] METHOD_CODES = {"mpesa", "bank", "cash", "cheque"};

    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private final UserRepository userRepository = new UserRepository();

    private final List<Tenant> activeTenants = new ArrayList<>();
    private final Map<String, String> shopNamesById = new HashMap<>();

    private Spinner spinnerTenant;
    private Spinner spinnerMethod;
    private TextInputEditText etMonthsPaid;
    private TextInputEditText etPaymentDate;
    private TextInputEditText etAmount;
    private TextInputEditText etPaymentPhone;
    private TextInputEditText etReceipt;
    private TextInputEditText etNotes;
    private TextView tvNoActiveTenants;
    private Button btnSavePayment;
    private long paymentDateMillis;
    private boolean amountManuallyEdited;

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

        setContentView(R.layout.activity_record_payment);

        View root = findViewById(R.id.recordPaymentRoot);
        View topBar = findViewById(R.id.recordPaymentTopBar);
        EdgeToEdge.applyTopInset(topBar);
        EdgeToEdge.applyBottomInset(root);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        spinnerTenant = findViewById(R.id.spinnerTenant);
        spinnerMethod = findViewById(R.id.spinnerMethod);
        etMonthsPaid = findViewById(R.id.etMonthsPaid);
        etPaymentDate = findViewById(R.id.etPaymentDate);
        etAmount = findViewById(R.id.etAmount);
        etPaymentPhone = findViewById(R.id.etPaymentPhone);
        etReceipt = findViewById(R.id.etReceipt);
        etNotes = findViewById(R.id.etNotes);
        tvNoActiveTenants = findViewById(R.id.tvNoActiveTenants);
        btnSavePayment = findViewById(R.id.btnSavePayment);

        String[] methodLabels = {
                getString(R.string.payments_method_mpesa), getString(R.string.payments_method_bank),
                getString(R.string.payments_method_cash), getString(R.string.payments_method_cheque)};
        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, methodLabels);
        methodAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMethod.setAdapter(methodAdapter);

        paymentDateMillis = System.currentTimeMillis();
        etPaymentDate.setText(DateCalculator.formatDdMmYyyy(paymentDateMillis));
        etPaymentDate.setOnClickListener(v -> showDatePicker());

        etMonthsPaid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateComputedAmount(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        etAmount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { amountManuallyEdited = true; }
            @Override public void afterTextChanged(Editable s) {}
        });
        spinnerTenant.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                amountManuallyEdited = false;
                updateComputedAmount();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        setFormEnabled(false);
        loadActiveTenants();

        btnSavePayment.setOnClickListener(v -> submit());
    }

    private void loadActiveTenants() {
        shopRepository.observeShopsOnce(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                shopNamesById.clear();
                for (Shop shop : shops) {
                    shopNamesById.put(shop.getShopId(), shop.getName() != null ? shop.getName() : shop.getShopId());
                }
                tenantRepository.observeTenantsOnce(new TenantRepository.TenantsListener() {
                    @Override
                    public void onTenants(List<Tenant> tenants) {
                        activeTenants.clear();
                        for (Tenant t : tenants) {
                            if (t != null && t.getShopId() != null && !t.getShopId().isEmpty()) activeTenants.add(t);
                        }
                        bindActiveTenants();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(RecordPaymentActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onError(String message) {
                Toast.makeText(RecordPaymentActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindActiveTenants() {
        boolean hasActive = !activeTenants.isEmpty();
        tvNoActiveTenants.setVisibility(hasActive ? View.GONE : View.VISIBLE);
        setFormEnabled(hasActive);

        if (hasActive) {
            List<String> names = new ArrayList<>();
            for (Tenant t : activeTenants) {
                String shopName = shopNamesById.get(t.getShopId());
                if (shopName == null) shopName = t.getShopId();
                names.add(t.getName() + " · " + shopName + " · TSh " + String.format(Locale.US, "%,d", t.getMonthlyRent()));
            }
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerTenant.setAdapter(spinnerAdapter);
            updateComputedAmount();
        }
    }

    private void setFormEnabled(boolean enabled) {
        spinnerTenant.setEnabled(enabled);
        spinnerMethod.setEnabled(enabled);
        etMonthsPaid.setEnabled(enabled);
        etPaymentDate.setEnabled(enabled);
        etAmount.setEnabled(enabled);
        etPaymentPhone.setEnabled(enabled);
        etReceipt.setEnabled(enabled);
        etNotes.setEnabled(enabled);
        btnSavePayment.setEnabled(enabled);
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

    /** Amount tracks months x rent automatically until the Super Admin types their own value -
     * matches the reference design's "computed, but overridable" behaviour. Recomputing here
     * intentionally does not flip amountManuallyEdited back on. */
    private void updateComputedAmount() {
        if (amountManuallyEdited) return;
        int selected = spinnerTenant.getSelectedItemPosition();
        if (selected < 0 || selected >= activeTenants.size()) return;
        int months = parseMonths();
        if (months <= 0) return;
        long amount = (long) months * activeTenants.get(selected).getMonthlyRent();
        etAmount.removeTextChangedListener(amountWatcher);
        etAmount.setText(String.valueOf(amount));
        etAmount.addTextChangedListener(amountWatcher);
    }

    private final TextWatcher amountWatcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) { amountManuallyEdited = true; }
        @Override public void afterTextChanged(Editable s) {}
    };

    private int parseMonths() {
        String text = textOf(etMonthsPaid);
        if (text.isEmpty()) return 0;
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long parseAmount() {
        String text = textOf(etAmount);
        if (text.isEmpty()) return 0;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void submit() {
        if (activeTenants.isEmpty()) return;

        int selected = spinnerTenant.getSelectedItemPosition();
        if (selected < 0 || selected >= activeTenants.size()) return;
        Tenant tenant = activeTenants.get(selected);

        int months = parseMonths();
        if (months <= 0) {
            Toast.makeText(this, R.string.tenants_error_invalid_months, Toast.LENGTH_SHORT).show();
            return;
        }
        long amount = parseAmount();
        if (amount <= 0) {
            Toast.makeText(this, R.string.payments_error_invalid_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        String paymentPhone = textOf(etPaymentPhone);
        if (paymentPhone.isEmpty()) {
            Toast.makeText(this, R.string.payments_error_phone_required, Toast.LENGTH_SHORT).show();
            return;
        }

        String methodCode = METHOD_CODES[Math.max(0, spinnerMethod.getSelectedItemPosition())];
        String receipt = textOf(etReceipt);
        String notes = textOf(etNotes);
        String recordedByUid = new AuthRepository().currentUid();

        setFormEnabled(false);
        userRepository.observeCurrentUserName(UserRole.SUPER_ADMIN, recordedByUid, recordedByName ->
                paymentRepository.recordPayment(tenant, months, paymentDateMillis, amount, methodCode,
                        receipt, notes, paymentPhone, recordedByUid, recordedByName,
                        new FirebaseManager.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                Toast.makeText(RecordPaymentActivity.this, R.string.payments_success, Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                setFormEnabled(true);
                                Toast.makeText(RecordPaymentActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
