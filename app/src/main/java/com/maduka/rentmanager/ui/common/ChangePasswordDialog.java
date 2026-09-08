package com.maduka.rentmanager.ui.common;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.FirebaseManager;

public class ChangePasswordDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.dialog_change_password, null, false);

        TextInputEditText etCurrentPassword = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNewPassword = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirmPassword = view.findViewById(R.id.etConfirmPassword);
        Button btnSavePassword = view.findViewById(R.id.btnSavePassword);

        btnSavePassword.setOnClickListener(v -> {
            String newPassword = etNewPassword.getText() != null ? etNewPassword.getText().toString() : "";
            String confirmPassword = etConfirmPassword.getText() != null ? etConfirmPassword.getText().toString() : "";

            if (newPassword.length() < 6) {
                MadukaToast.show(requireActivity(), getString(R.string.change_password_error_length), MadukaToast.Kind.BAD);
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                MadukaToast.show(requireActivity(), getString(R.string.change_password_error_mismatch), MadukaToast.Kind.BAD);
                return;
            }

            new AuthRepository().changePassword(newPassword, new FirebaseManager.Callback<Void>() {
                @Override
                public void onSuccess(Void result) {
                    MadukaToast.show(requireActivity(), getString(R.string.change_password_success), MadukaToast.Kind.OK);
                    dismiss();
                }

                @Override
                public void onError(String message) {
                    MadukaToast.show(requireActivity(), message, MadukaToast.Kind.BAD);
                }
            });
        });

        return new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }
}
