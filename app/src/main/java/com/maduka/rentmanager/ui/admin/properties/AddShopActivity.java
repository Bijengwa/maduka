package com.maduka.rentmanager.ui.admin.properties;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

import java.util.List;

/** Full-screen replacement for the old AddShopSheet dialog. Super Admin now names a new shop
 * freely (uniqueness enforced case-insensitively, trimmed, by ShopRepository.addShop against a
 * fresh observeShopsOnce read) instead of picking from a fixed A1..A10 slot list - shopId is
 * now an internal Firebase push key.
 *
 * Super-Admin-only: PropertiesFragment only wires the launching click listener for that role,
 * but since this is still a normal in-app Activity (not itself aware of who launched it), it
 * also refuses to open for any other role passed via EXTRA_ROLE - a role guard in the code
 * path itself, not just a hidden button. */
public class AddShopActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";

    private final ShopRepository shopRepository = new ShopRepository();

    private TextInputLayout tilName;
    private TextInputLayout tilRent;
    private TextInputEditText etName;
    private TextInputEditText etRent;
    private Button btnAdd;

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

        setContentView(R.layout.activity_add_shop);

        View root = findViewById(R.id.addShopRoot);
        View topBar = findViewById(R.id.addShopTopBar);
        EdgeToEdge.applyTopInset(topBar);
        EdgeToEdge.applyBottomInset(root);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        tilName = findViewById(R.id.tilName);
        tilRent = findViewById(R.id.tilRent);
        etName = findViewById(R.id.etName);
        etRent = findViewById(R.id.etRent);
        btnAdd = findViewById(R.id.btnAdd);

        btnAdd.setOnClickListener(v -> submit());
    }

    private void submit() {
        tilName.setError(null);
        tilRent.setError(null);

        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String rentText = etRent.getText() != null ? etRent.getText().toString().trim() : "";

        boolean valid = true;
        if (name.isEmpty()) {
            tilName.setError(getString(R.string.users_error_required));
            valid = false;
        }

        long rent = 0;
        try {
            rent = Long.parseLong(rentText);
            if (rent <= 0) throw new NumberFormatException("non-positive");
        } catch (NumberFormatException e) {
            tilRent.setError(getString(R.string.properties_error_invalid_rent));
            valid = false;
        }
        if (!valid) return;

        String finalName = name;
        long finalRent = rent;

        btnAdd.setEnabled(false);
        shopRepository.observeShopsOnce(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                shopRepository.addShop(finalName, finalRent, shops, new FirebaseManager.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        btnAdd.setEnabled(true);
                        tilName.setError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                btnAdd.setEnabled(true);
                tilName.setError(message);
            }
        });
    }
}
