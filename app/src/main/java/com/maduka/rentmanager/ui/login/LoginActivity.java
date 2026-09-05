package com.maduka.rentmanager.ui.login;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.shell.MainActivity;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import com.maduka.rentmanager.util.VerseProvider;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class LoginActivity extends AppCompatActivity {
    public static final String EXTRA_ROLE = "extra_role";

    private final AuthRepository authRepository = new AuthRepository();
    private UserRole selectedRole = UserRole.ADMIN;

    private Button btnSuperAdmin, btnAdmin, btnTenant, btnSignIn;
    private TextInputEditText etIdentifier, etPassword;
    private CheckBox cbRememberMe;
    private View verseContainer;
    private TextView tvVerseText, tvVerseReference;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnSuperAdmin = findViewById(R.id.btnRoleSuperAdmin);
        btnAdmin = findViewById(R.id.btnRoleAdmin);
        btnTenant = findViewById(R.id.btnRoleTenant);
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnSignIn = findViewById(R.id.btnSignIn);
        verseContainer = findViewById(R.id.verseContainer);
        tvVerseText = findViewById(R.id.tvVerseText);
        tvVerseReference = findViewById(R.id.tvVerseReference);

        btnSuperAdmin.setOnClickListener(v -> selectRole(UserRole.SUPER_ADMIN));
        btnAdmin.setOnClickListener(v -> selectRole(UserRole.ADMIN));
        btnTenant.setOnClickListener(v -> selectRole(UserRole.TENANT));
        btnSignIn.setOnClickListener(v -> signIn());

        Prefs prefs = Prefs.get(this);
        if (prefs.isRememberMe() && prefs.rememberedIdentifier() != null) {
            etIdentifier.setText(prefs.rememberedIdentifier());
            selectRole(UserRole.valueOf(prefs.rememberedRole()));
        } else {
            selectRole(UserRole.ADMIN);
        }
    }

    private void selectRole(UserRole role) {
        selectedRole = role;
        btnSuperAdmin.setSelected(role == UserRole.SUPER_ADMIN);
        btnAdmin.setSelected(role == UserRole.ADMIN);
        btnTenant.setSelected(role == UserRole.TENANT);

        if (role == UserRole.TENANT) {
            verseContainer.setVisibility(View.GONE);
        } else {
            VerseProvider.Verse verse = VerseProvider.pickVerse(Prefs.get(this).signInCount());
            tvVerseText.setText("“" + verse.text + "”");
            tvVerseReference.setText("— " + verse.reference);
            verseContainer.setVisibility(View.VISIBLE);
        }
    }

    private void signIn() {
        String identifier = String.valueOf(etIdentifier.getText());
        String password = String.valueOf(etPassword.getText());
        btnSignIn.setEnabled(false);

        authRepository.signIn(selectedRole, identifier, password, new FirebaseManager.Callback<AuthRepository.AuthResult>() {
            @Override
            public void onSuccess(AuthRepository.AuthResult result) {
                Prefs prefs = Prefs.get(LoginActivity.this);
                prefs.setRememberMe(cbRememberMe.isChecked(), selectedRole.name(), identifier);
                prefs.bumpSignInCount();

                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra(EXTRA_ROLE, result.role.name());
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String message) {
                btnSignIn.setEnabled(true);
                etPassword.setError(getString(R.string.login_error_generic));
            }
        });
    }
}
