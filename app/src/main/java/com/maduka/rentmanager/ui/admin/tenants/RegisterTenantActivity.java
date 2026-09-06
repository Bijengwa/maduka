package com.maduka.rentmanager.ui.admin.tenants;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

import java.util.ArrayList;
import java.util.List;

/** Full-screen registration for a new tenant, gated to shops that are currently vacant
 * (shop.isOccupied() == false) - a shop that already has a tenant cannot be selected until its
 * tenant relationship ends and it goes back to vacant. Account creation goes through
 * TenantRepository.registerTenant, which uses the same secondary-FirebaseAuth pattern as
 * UserRepository.registerAdmin, so the currently signed-in Admin/Super Admin's own session is
 * never disturbed. */
public class RegisterTenantActivity extends AppCompatActivity {
    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();

    private final List<Shop> vacantShops = new ArrayList<>();

    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private Spinner spinnerShop;
    private TextView tvNoVacantShops;
    private Button btnRegister;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        tvNoVacantShops = findViewById(R.id.tvNoVacantShops);
        btnRegister = findViewById(R.id.btnRegister);

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
        }
    }

    private void setFormEnabled(boolean enabled) {
        etName.setEnabled(enabled);
        etPhone.setEnabled(enabled);
        etEmail.setEnabled(enabled);
        etPassword.setEnabled(enabled);
        spinnerShop.setEnabled(enabled);
        btnRegister.setEnabled(enabled);
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

        int selected = spinnerShop.getSelectedItemPosition();
        if (selected < 0 || selected >= vacantShops.size()) return;
        Shop selectedShop = vacantShops.get(selected);

        setFormEnabled(false);
        tenantRepository.registerTenant(name, phone, email, password, selectedShop, new FirebaseManager.Callback<Void>() {
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
