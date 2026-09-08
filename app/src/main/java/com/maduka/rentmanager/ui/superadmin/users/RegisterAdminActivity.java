package com.maduka.rentmanager.ui.superadmin.users;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import com.maduka.rentmanager.ui.common.MadukaToast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

/** Full-screen replacement for the old RegisterAdminSheet dialog. Account creation goes through
 * UserRepository.registerAdmin, which uses a secondary FirebaseAuth instance so the Super
 * Admin's own signed-in session is never disturbed - see SecondaryAuthProvider. The validation
 * and submission logic below is unchanged from RegisterAdminSheet - only the container (dialog
 * -> full-screen Activity) and visual presentation changed.
 *
 * Super-Admin-only: UsersFragment only wires the launching click listener for that role; this
 * Activity also refuses to open for any other role passed via EXTRA_ROLE, matching the same
 * code-path guard already applied to AddShopActivity/RegisterTenantActivity. */
public class RegisterAdminActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";

    private final UserRepository userRepository = new UserRepository();

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

        setContentView(R.layout.activity_register_admin);

        View root = findViewById(R.id.registerAdminRoot);
        View topBar = findViewById(R.id.registerAdminTopBar);
        EdgeToEdge.applyTopInset(topBar);
        EdgeToEdge.applyBottomInset(root);

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        TextInputEditText etName = findViewById(R.id.etName);
        TextInputEditText etPhone = findViewById(R.id.etPhone);
        TextInputEditText etEmail = findViewById(R.id.etEmail);
        TextInputEditText etPassword = findViewById(R.id.etPassword);
        Button btnRegister = findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> {
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

            btnRegister.setEnabled(false);
            userRepository.registerAdmin(name, phone, email, password, new FirebaseManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    MadukaToast.show(RegisterAdminActivity.this, getString(R.string.users_register_success), MadukaToast.Kind.OK);
                    finish();
                }

                @Override
                public void onError(String message) {
                    btnRegister.setEnabled(true);
                    MadukaToast.show(RegisterAdminActivity.this, message, MadukaToast.Kind.BAD);
                }
            });
        });
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
