package com.maduka.rentmanager.ui.admin.properties;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Shops/Maduka screen: a live-computed portfolio summary (units/occupied/empty/overdue),
 * All/Occupied/Empty/Overdue filters, and per-shop cards joined against their occupying tenant.
 * Shown to both Admin and Super Admin (bottom_nav_admin.xml and bottom_nav_super_admin.xml both
 * carry nav_properties) - the data itself is identical for both roles, only the "Add Shop"
 * mutation control is Super-Admin-only. */
public class PropertiesFragment extends Fragment {
    private static final String ARG_ROLE = "arg_role";

    private enum Filter { ALL, OCCUPIED, EMPTY, OVERDUE }

    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();
    private UserRole role;
    private Filter filter = Filter.ALL;

    private List<Shop> allShops = new ArrayList<>();
    private Map<String, Tenant> tenantByShopId = new HashMap<>();

    private RecyclerView recyclerShops;
    private TextView tvEmpty;
    private TextView tvSubtitle;
    private TextView tvStatUnits, tvStatUnitsSub, tvStatOccupied, tvStatOccupiedSub, tvStatEmpty, tvStatEmptySub, tvStatOverdue, tvStatOverdueSub;
    private TextView filterAll, filterOccupied, filterEmpty, filterOverdue;
    private View btnAddShop;
    private ShopAdapter adapter;
    private ValueEventListener shopsRegistration;
    private ValueEventListener tenantsRegistration;

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
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvStatUnits = view.findViewById(R.id.tvStatUnits);
        tvStatUnitsSub = view.findViewById(R.id.tvStatUnitsSub);
        tvStatOccupied = view.findViewById(R.id.tvStatOccupied);
        tvStatOccupiedSub = view.findViewById(R.id.tvStatOccupiedSub);
        tvStatEmpty = view.findViewById(R.id.tvStatEmpty);
        tvStatEmptySub = view.findViewById(R.id.tvStatEmptySub);
        tvStatOverdue = view.findViewById(R.id.tvStatOverdue);
        tvStatOverdueSub = view.findViewById(R.id.tvStatOverdueSub);
        filterAll = view.findViewById(R.id.filterAll);
        filterOccupied = view.findViewById(R.id.filterOccupied);
        filterEmpty = view.findViewById(R.id.filterEmpty);
        filterOverdue = view.findViewById(R.id.filterOverdue);
        btnAddShop = view.findViewById(R.id.btnAddShop);

        adapter = new ShopAdapter();
        recyclerShops.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerShops.setAdapter(adapter);

        // Register Shop is Super-Admin-only: not just hidden, Admin never even wires the
        // click listener that would launch AddShopActivity.
        if (role == UserRole.SUPER_ADMIN) {
            btnAddShop.setVisibility(View.VISIBLE);
            btnAddShop.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), AddShopActivity.class);
                intent.putExtra(AddShopActivity.EXTRA_ROLE, role.name());
                startActivity(intent);
            });
        } else {
            btnAddShop.setVisibility(View.GONE);
        }

        // Overdue-tenant Yupo/Hayupo is an operational mutation - Super-Admin-only, same
        // pattern as Add Shop above: Admin never gets the listener wired at all.
        adapter.setShowPresenceActions(role == UserRole.SUPER_ADMIN);
        adapter.setOnPresenceDecisionListener(new ShopAdapter.OnPresenceDecisionListener() {
            @Override
            public void onYupo(Tenant tenant) {
                tenantRepository.setPresence(tenant.getUid(), PresenceStatus.YUPO, System.currentTimeMillis(),
                        new FirebaseManager.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!canTouchViews()) return;
                                Toast.makeText(getContext(), R.string.shops_presence_yupo_success, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                if (!canTouchViews()) return;
                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
            }

            @Override
            public void onHayupo(Tenant tenant) {
                tenantRepository.endTenancy(tenant.getUid(), tenant.getShopId(), System.currentTimeMillis(),
                        new FirebaseManager.Callback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                if (!canTouchViews()) return;
                                String shopName = shopNameFor(tenant.getShopId());
                                Toast.makeText(getContext(),
                                        getString(R.string.tenants_end_tenancy_success, tenant.getName(), shopName),
                                        Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                if (!canTouchViews()) return;
                                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        });

        filterAll.setOnClickListener(v -> setFilter(Filter.ALL));
        filterOccupied.setOnClickListener(v -> setFilter(Filter.OCCUPIED));
        filterEmpty.setOnClickListener(v -> setFilter(Filter.EMPTY));
        filterOverdue.setOnClickListener(v -> setFilter(Filter.OVERDUE));
        updateFilterChipStyles();

        tenantsRegistration = tenantRepository.observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                if (!canTouchViews()) return;
                tenantByShopId = new HashMap<>();
                for (Tenant t : tenants) {
                    if (t != null && t.getShopId() != null && !t.getShopId().isEmpty()) {
                        tenantByShopId.put(t.getShopId(), t);
                    }
                }
                render();
            }

            @Override
            public void onError(String message) {
                if (!canTouchViews()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        shopsRegistration = shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!canTouchViews()) return;
                allShops = shops != null ? shops : new ArrayList<>();
                render();
            }

            @Override
            public void onError(String message) {
                if (!canTouchViews()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
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

    private String shopNameFor(String shopId) {
        if (shopId == null) return "";
        for (Shop shop : allShops) {
            if (shop != null && shopId.equals(shop.getShopId())) {
                return shop.getName() != null && !shop.getName().isEmpty() ? shop.getName() : shopId;
            }
        }
        return shopId;
    }

    /** "A1 · A2 · A3 · +2 more" - a sensible compact representation that never assumes a fixed
     * shop count. Caps at 3 names so a large portfolio doesn't overflow the card. */
    private String compactShopList(List<Shop> shops) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(shops.size(), 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append(" · ");
            Shop shop = shops.get(i);
            sb.append(shop.getName() != null && !shop.getName().isEmpty() ? shop.getName() : shop.getShopId());
        }
        int remaining = shops.size() - shown;
        if (remaining > 0) sb.append(" ").append(getString(R.string.shops_stat_list_more_format, remaining));
        return sb.toString();
    }

    private void setFilter(Filter newFilter) {
        filter = newFilter;
        updateFilterChipStyles();
        render();
    }

    private void updateFilterChipStyles() {
        style(filterAll, filter == Filter.ALL);
        style(filterOccupied, filter == Filter.OCCUPIED);
        style(filterEmpty, filter == Filter.EMPTY);
        style(filterOverdue, filter == Filter.OVERDUE);
    }

    private void style(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.shape_pill_selected : R.drawable.shape_pill_unselected);
        chip.setTextColor(selected
                ? androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_accent_300)
                : androidx.core.content.ContextCompat.getColor(requireContext(), R.color.md_neutral_500));
    }

    /** Recomputes the summary stats and the filtered, tenant-joined row list from the two live
     * feeds (shops, tenants) whenever either one changes. No stat here is hardcoded - every
     * number is derived from allShops/tenantByShopId at render time. */
    private void render() {
        if (!canTouchViews()) return;
        long now = System.currentTimeMillis();
        int units = allShops.size();
        int occupied = 0;
        List<Shop> emptyShops = new ArrayList<>();
        List<Shop> overdueShops = new ArrayList<>();
        List<ShopAdapter.Row> allRows = new ArrayList<>();
        for (Shop shop : allShops) {
            if (shop == null) continue;
            Tenant tenant = shop.getShopId() != null ? tenantByShopId.get(shop.getShopId()) : null;
            boolean isOccupied = shop.isOccupied() && tenant != null;
            if (isOccupied) {
                occupied++;
                if (DateCalculator.isOverdue(tenant.getDueDate(), now)) overdueShops.add(shop);
            } else {
                emptyShops.add(shop);
            }
            allRows.add(new ShopAdapter.Row(shop, tenant));
        }
        int empty = units - occupied;
        int overdueCount = overdueShops.size();

        tvSubtitle.setText(getString(R.string.shops_subtitle_format, units));
        tvStatUnits.setText(String.valueOf(units));
        tvStatUnitsSub.setText("");
        tvStatOccupied.setText(String.valueOf(occupied));
        tvStatOccupiedSub.setText(units > 0
                ? Math.round(occupied * 100f / units) + "%"
                : "");
        tvStatEmpty.setText(String.valueOf(empty));
        tvStatEmptySub.setText(compactShopList(emptyShops));
        tvStatOverdue.setText(String.valueOf(overdueCount));
        tvStatOverdueSub.setText(compactShopList(overdueShops));

        List<ShopAdapter.Row> filtered = new ArrayList<>();
        for (ShopAdapter.Row row : allRows) {
            boolean isOccupied = row.shop.isOccupied() && row.tenant != null;
            boolean isOverdue = isOccupied && DateCalculator.isOverdue(row.tenant.getDueDate(), now);
            switch (filter) {
                case OCCUPIED:
                    if (isOccupied) filtered.add(row);
                    break;
                case EMPTY:
                    if (!isOccupied) filtered.add(row);
                    break;
                case OVERDUE:
                    if (isOverdue) filtered.add(row);
                    break;
                default:
                    filtered.add(row);
            }
        }

        adapter.submitList(filtered);
        boolean noShopsAtAll = allShops.isEmpty();
        boolean noResultsForFilter = !noShopsAtAll && filtered.isEmpty();
        recyclerShops.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        tvEmpty.setText(noResultsForFilter ? R.string.shops_no_results : R.string.properties_empty);
    }
}
