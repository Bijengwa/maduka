package com.maduka.rentmanager.ui.superadmin.users;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.UserRepository;

/** Dialog for Super Admin to register a new Admin account. Account creation goes through
 * UserRepository.registerAdmin, which uses a secondary FirebaseAuth instance so the Super
 * Admin's own signed-in session is never disturbed - see SecondaryAuthProvider. */
public class RegisterAdminSheet extends DialogFragment {
    private final UserRepository userRepository = new UserRepository();

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.sheet_register_admin, null, false);

        TextInputEditText etName = view.findViewById(R.id.etName);
        TextInputEditText etPhone = view.findViewById(R.id.etPhone);
        TextInputEditText etEmail = view.findViewById(R.id.etEmail);
        TextInputEditText etPassword = view.findViewById(R.id.etPassword);
        Button btnRegister = view.findViewById(R.id.btnRegister);

        btnRegister.setOnClickListener(v -> {
            String name = textOf(etName);
            String phone = textOf(etPhone);
            String email = textOf(etEmail);
            String password = textOf(etPassword);

            if (name.isEmpty() || phone.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(getContext(), R.string.users_error_required, Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(getContext(), R.string.change_password_error_length, Toast.LENGTH_SHORT).show();
                return;
            }

            btnRegister.setEnabled(false);
            userRepository.registerAdmin(name, phone, email, password, new FirebaseManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    if (!isAdded()) return;
                    Toast.makeText(getContext(), R.string.users_register_success, Toast.LENGTH_SHORT).show();
                    dismiss();
                }

                @Override
                public void onError(String message) {
                    if (!isAdded()) return;
                    btnRegister.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    private String textOf(TextInputEditText et) {
        return et.getText() != null ? et.getText().toString().trim() : "";
    }
}
