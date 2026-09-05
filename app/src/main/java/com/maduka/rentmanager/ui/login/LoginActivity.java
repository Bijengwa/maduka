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

    private Button btnLanguage, btnSignIn;
    private TextInputEditText etIdentifier, etPassword;
    private CheckBox cbRememberMe;
    private TextView tvVerseText, tvVerseReference;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        btnLanguage = findViewById(R.id.btnLanguage);
        etIdentifier = findViewById(R.id.etIdentifier);
        etPassword = findViewById(R.id.etPassword);
        cbRememberMe = findViewById(R.id.cbRememberMe);
        btnSignIn = findViewById(R.id.btnSignIn);
        tvVerseText = findViewById(R.id.tvVerseText);
        tvVerseReference = findViewById(R.id.tvVerseReference);

        Prefs prefs = Prefs.get(this);
        btnLanguage.setText(prefs.language().equals("sw") ? "SW" : "EN");
        btnLanguage.setOnClickListener(v -> {
            String next = prefs.language().equals("sw") ? "en" : "sw";
            prefs.setLanguage(next);
            recreate();
        });

        VerseProvider.Verse verse = VerseProvider.pickVerse(prefs.signInCount());
        tvVerseText.setText("“" + verse.text + "”");
        tvVerseReference.setText("— " + verse.reference);

        btnSignIn.setOnClickListener(v -> signIn());

        if (prefs.isRememberMe() && prefs.rememberedIdentifier() != null) {
            etIdentifier.setText(prefs.rememberedIdentifier());
        }
    }

    private void signIn() {
        String identifier = String.valueOf(etIdentifier.getText());
        String password = String.valueOf(etPassword.getText());
        btnSignIn.setEnabled(false);

        authRepository.signIn(identifier, password, new FirebaseManager.Callback<AuthRepository.AuthResult>() {
            @Override
            public void onSuccess(AuthRepository.AuthResult result) {
                Prefs prefs = Prefs.get(LoginActivity.this);
                prefs.setRememberMe(cbRememberMe.isChecked(), result.role.name(), identifier);
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
