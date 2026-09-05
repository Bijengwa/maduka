package com.maduka.rentmanager.ui.admin.properties;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.textfield.TextInputEditText;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;

import java.util.ArrayList;
import java.util.List;

/** Dialog for Super Admin to register a new shop. Only offers shop IDs (A1..A10) that
 * are not already registered, per the current in-memory list from PropertiesFragment's
 * live ShopRepository.observeShops feed. */
public class AddShopSheet extends DialogFragment {
    private static final String ARG_EXISTING_IDS = "arg_existing_ids";
    private static final String[] ALL_SHOP_IDS =
            {"A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10"};

    private final ShopRepository shopRepository = new ShopRepository();

    public static AddShopSheet newInstance(List<String> existingShopIds) {
        AddShopSheet sheet = new AddShopSheet();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_EXISTING_IDS, new ArrayList<>(existingShopIds));
        sheet.setArguments(args);
        return sheet;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.sheet_add_shop, null, false);

        Spinner spinnerShopId = view.findViewById(R.id.spinnerShopId);
        TextInputEditText etRent = view.findViewById(R.id.etRent);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        TextView tvAllRegistered = view.findViewById(R.id.tvAllRegistered);

        List<String> existingIds = getArguments() != null
                ? getArguments().getStringArrayList(ARG_EXISTING_IDS)
                : new ArrayList<>();
        if (existingIds == null) existingIds = new ArrayList<>();

        List<String> available = new ArrayList<>();
        for (String id : ALL_SHOP_IDS) {
            if (!existingIds.contains(id)) available.add(id);
        }

        if (available.isEmpty()) {
            spinnerShopId.setVisibility(View.GONE);
            etRent.setEnabled(false);
            btnAdd.setVisibility(View.GONE);
            tvAllRegistered.setVisibility(View.VISIBLE);
        } else {
            tvAllRegistered.setVisibility(View.GONE);
            ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, available);
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerShopId.setAdapter(spinnerAdapter);

            btnAdd.setOnClickListener(v -> {
                String shopId = (String) spinnerShopId.getSelectedItem();
                String rentText = etRent.getText() != null ? etRent.getText().toString().trim() : "";
                long rent;
                try {
                    rent = Long.parseLong(rentText);
                    if (rent <= 0) throw new NumberFormatException("non-positive");
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), R.string.properties_error_invalid_rent, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (shopId == null) return;

                btnAdd.setEnabled(false);
                shopRepository.addShop(shopId, rent, new FirebaseManager.Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        if (!isAdded()) return;
                        dismiss();
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        btnAdd.setEnabled(true);
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }
}
