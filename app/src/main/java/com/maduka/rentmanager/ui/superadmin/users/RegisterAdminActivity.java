package com.maduka.rentmanager.ui.superadmin.users;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

/** Full-screen replacement for the old RegisterAdminSheet dialog. Account creation goes through
 * UserRepository.registerAdmin, which uses a secondary FirebaseAuth instance so the Super
 * Admin's own signed-in session is never disturbed - see SecondaryAuthProvider. The validation
 * and submission logic below is unchanged from RegisterAdminSheet - only the container (dialog
 * -> full-screen Activity) and visual presentation changed. */
public class RegisterAdminActivity extends AppCompatActivity {
    private final UserRepository userRepository = new UserRepository();

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
                Toast.makeText(this, R.string.users_error_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, R.string.change_password_error_length, Toast.LENGTH_SHORT).show();
                return;
            }

            btnRegister.setEnabled(false);
            userRepository.registerAdmin(name, phone, email, password, new FirebaseManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    Toast.makeText(RegisterAdminActivity.this, R.string.users_register_success, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(String message) {
                    btnRegister.setEnabled(true);
                    Toast.makeText(RegisterAdminActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
