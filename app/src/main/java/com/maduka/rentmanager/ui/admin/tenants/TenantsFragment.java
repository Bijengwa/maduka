package com.maduka.rentmanager.ui.admin.tenants;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import com.maduka.rentmanager.ui.common.MadukaToast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.FirebaseManager;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reachable by both Admin and Super Admin (bottom_nav_admin.xml and bottom_nav_super_admin.xml
 * both carry nav_tenants), but the two roles now diverge: Super Admin can register a tenant,
 * Admin gets a strictly read-only view (matches the approved design's "The super admin
 * registers new tenants" copy) - this used to show the register action to both roles with
 * zero branching, which was the bug this task fixes. */
public class TenantsFragment extends Fragment {
    private static final String ARG_ROLE = "arg_role";

    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();
    private UserRole role;

    private RecyclerView recyclerTenants;
    private TextView tvEmpty;
    private TextView tvSubtitle;
    private Button btnRegisterTenant;
    private TextView tvReadonlyNote;
    private TenantAdapter adapter;
    private ValueEventListener shopsRegistration;
    private ValueEventListener tenantsRegistration;

    public static TenantsFragment newInstance(UserRole role) {
        TenantsFragment fragment = new TenantsFragment();
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
        return inflater.inflate(R.layout.fragment_tenants, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerTenants = view.findViewById(R.id.recyclerTenants);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        btnRegisterTenant = view.findViewById(R.id.btnRegisterTenant);
        tvReadonlyNote = view.findViewById(R.id.tvReadonlyNote);

        adapter = new TenantAdapter();
        recyclerTenants.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerTenants.setAdapter(adapter);
        adapter.setShowPresenceActions(role == UserRole.SUPER_ADMIN);
        adapter.setOnPresenceDecisionListener(new TenantAdapter.OnPresenceDecisionListener() {
            @Override
            public void onYupo(Tenant tenant) {
                tenantRepository.setPresence(tenant.getUid(), com.maduka.rentmanager.data.model.PresenceStatus.YUPO,
                        System.currentTimeMillis(), new FirebaseManager.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!canTouchViews()) return;
                                MadukaToast.show(requireActivity(), getString(R.string.shops_presence_yupo_success), MadukaToast.Kind.OK);
                            }

                            @Override
                            public void onError(String message) {
                                if (!canTouchViews()) return;
                                MadukaToast.show(requireActivity(), message, MadukaToast.Kind.BAD);
                            }
                        });
            }

            @Override
            public void onHayupo(Tenant tenant) {
                String shopName = adapter.shopNameFor(tenant.getShopId());
                tenantRepository.endTenancy(tenant.getUid(), tenant.getShopId(), System.currentTimeMillis(),
                        new FirebaseManager.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!canTouchViews()) return;
                                MadukaToast.show(requireActivity(),
                                        getString(R.string.tenants_end_tenancy_success, tenant.getName(), shopName),
                                        MadukaToast.Kind.OK);
                            }

                            @Override
                            public void onError(String message) {
                                if (!canTouchViews()) return;
                                MadukaToast.show(requireActivity(), message, MadukaToast.Kind.BAD);
                            }
                        });
            }
        });

        // Register Tenant is Super-Admin-only: Admin never gets the click listener wired,
        // not merely a hidden button - it sees the read-only note instead.
        if (role == UserRole.SUPER_ADMIN) {
            btnRegisterTenant.setVisibility(View.VISIBLE);
            tvReadonlyNote.setVisibility(View.GONE);
            btnRegisterTenant.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), RegisterTenantActivity.class);
                intent.putExtra(RegisterTenantActivity.EXTRA_ROLE, role.name());
                startActivity(intent);
            });
        } else {
            btnRegisterTenant.setVisibility(View.GONE);
            tvReadonlyNote.setVisibility(View.VISIBLE);
        }

        shopsRegistration = shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!canTouchViews()) return;
                Map<String, String> shopNames = new HashMap<>();
                for (Shop shop : shops) {
                    shopNames.put(shop.getShopId(), shop.getName() != null ? shop.getName() : shop.getShopId());
                }
                adapter.setShopNames(shopNames);
            }

            @Override
            public void onError(String message) {
                if (!canTouchViews()) return;
                MadukaToast.show(requireActivity(), message, MadukaToast.Kind.BAD);
            }
        });

        tenantsRegistration = tenantRepository.observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> allTenants) {
                if (!canTouchViews()) return;
                // A tenant whose tenancy has ended has shopId cleared (TenantRepository.endTenancy)
                // - they keep their historical record but no longer show as an active tenant here.
                List<Tenant> tenants = new ArrayList<>();
                for (Tenant t : allTenants) {
                    if (t != null && t.getShopId() != null && !t.getShopId().isEmpty()) tenants.add(t);
                }

                adapter.submitList(tenants);
                boolean empty = tenants.isEmpty();
                recyclerTenants.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

                long now = System.currentTimeMillis();
                int overdueCount = 0;
                for (Tenant t : tenants) {
                    if (DateCalculator.isOverdue(t.getDueDate(), now)) overdueCount++;
                }
                tvSubtitle.setText(getString(R.string.tenants_subtitle_format, tenants.size(), overdueCount));
            }

            @Override
            public void onError(String message) {
                if (!canTouchViews()) return;
                MadukaToast.show(requireActivity(), message, MadukaToast.Kind.BAD);
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (shopsRegistration != null) {
            shopRepository.stopObservingShops(shopsRegistration);
            shopsRegistration = null;
        }
        if (tenantsRegistration != null) {
            tenantRepository.stopObservingTenants(tenantsRegistration);
            tenantsRegistration = null;
        }
        super.onDestroyView();
    }

    private boolean canTouchViews() {
        return isAdded() && getView() != null;
    }
}
