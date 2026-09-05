package com.maduka.rentmanager.ui.admin.properties;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.UserRole;

import java.util.ArrayList;
import java.util.List;

public class PropertiesFragment extends Fragment {
    private static final String ARG_ROLE = "arg_role";

    private final ShopRepository shopRepository = new ShopRepository();
    private UserRole role;
    private List<Shop> currentShops = new ArrayList<>();

    private RecyclerView recyclerShops;
    private TextView tvEmpty;
    private Button btnAddShop;
    private ShopAdapter adapter;

    /** Passes the signed-in user's role into the fragment so it knows whether to show
     * the "Add Shop" action (Super Admin only) or a read-only list (Admin). */
    public static PropertiesFragment newInstance(UserRole role) {
        PropertiesFragment fragment = new PropertiesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        String roleName = args != null ? args.getString(ARG_ROLE) : null;
        role = roleName != null ? UserRole.valueOf(roleName) : UserRole.ADMIN;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_properties, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerShops = view.findViewById(R.id.recyclerShops);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnAddShop = view.findViewById(R.id.btnAddShop);

        adapter = new ShopAdapter();
        recyclerShops.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerShops.setAdapter(adapter);

        if (role == UserRole.SUPER_ADMIN) {
            btnAddShop.setVisibility(View.VISIBLE);
            btnAddShop.setOnClickListener(v -> {
                List<String> existingIds = new ArrayList<>();
                for (Shop s : currentShops) existingIds.add(s.getShopId());
                AddShopSheet.newInstance(existingIds).show(getChildFragmentManager(), "add_shop");
            });
        } else {
            btnAddShop.setVisibility(View.GONE);
        }

        shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!isAdded()) return;
                currentShops = shops;
                adapter.submitList(shops);
                boolean empty = shops.isEmpty();
                recyclerShops.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
