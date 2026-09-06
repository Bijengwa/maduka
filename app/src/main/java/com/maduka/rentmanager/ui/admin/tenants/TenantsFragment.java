package com.maduka.rentmanager.ui.admin.tenants;

import android.content.Intent;
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
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reachable by both Admin and Super Admin (bottom_nav_admin.xml and bottom_nav_super_admin.xml
 * both carry nav_tenants) - no role branching needed, per the brief: tenant registration isn't
 * restricted to a role the way shop/admin registration is. */
public class TenantsFragment extends Fragment {
    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();

    private RecyclerView recyclerTenants;
    private TextView tvEmpty;
    private Button btnRegisterTenant;
    private TenantAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenants, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerTenants = view.findViewById(R.id.recyclerTenants);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnRegisterTenant = view.findViewById(R.id.btnRegisterTenant);

        adapter = new TenantAdapter();
        recyclerTenants.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerTenants.setAdapter(adapter);

        btnRegisterTenant.setOnClickListener(v ->
                startActivity(new Intent(getContext(), RegisterTenantActivity.class)));

        shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!isAdded()) return;
                Map<String, String> shopNames = new HashMap<>();
                for (Shop shop : shops) {
                    shopNames.put(shop.getShopId(), shop.getName() != null ? shop.getName() : shop.getShopId());
                }
                adapter.setShopNames(shopNames);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        tenantRepository.observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                if (!isAdded()) return;
                adapter.submitList(tenants);
                boolean empty = tenants.isEmpty();
                recyclerTenants.setVisibility(empty ? View.GONE : View.VISIBLE);
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
