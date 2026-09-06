package com.maduka.rentmanager.ui.admin.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.Prefs;
import com.maduka.rentmanager.util.VerseProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Admin/Super Admin dashboard: a live-computed portfolio summary (same units/occupied/empty/
 * overdue join as the Shops screen) plus a "next due" callout - no mutation actions here at all
 * (recording/confirming payments is out of this build's scope; see the design amendment doc),
 * so the screen is identical, read-only content for both roles. KJV verse shown here only, per
 * Global Constraints - never on the Tenant side. */
public class AdminDashboardFragment extends Fragment {
    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();

    private List<Shop> allShops = new ArrayList<>();
    private Map<String, Tenant> tenantByShopId = new HashMap<>();

    private TextView tvSubtitle, tvVerseText, tvVerseReference;
    private TextView tvStatUnits, tvStatOccupied, tvStatEmpty, tvStatOverdue, tvNextDue;
    private ValueEventListener shopsRegistration, tenantsRegistration;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvVerseText = view.findViewById(R.id.tvVerseText);
        tvVerseReference = view.findViewById(R.id.tvVerseReference);
        tvStatUnits = view.findViewById(R.id.tvStatUnits);
        tvStatOccupied = view.findViewById(R.id.tvStatOccupied);
        tvStatEmpty = view.findViewById(R.id.tvStatEmpty);
        tvStatOverdue = view.findViewById(R.id.tvStatOverdue);
        tvNextDue = view.findViewById(R.id.tvNextDue);

        Prefs prefs = Prefs.get(requireContext());
        int verseIndex = VerseProvider.indexFor(prefs.signInCount());
        String[] verseTexts = getResources().getStringArray(R.array.verse_texts);
        String[] verseReferences = getResources().getStringArray(R.array.verse_references);
        tvVerseText.setText("“" + verseTexts[verseIndex] + "”");
        tvVerseReference.setText("— " + verseReferences[verseIndex]);

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
            public void onError(String message) { }
        });

        shopsRegistration = shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!canTouchViews()) return;
                allShops = shops != null ? shops : new ArrayList<>();
                render();
            }

            @Override
            public void onError(String message) { }
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

    private void render() {
        long now = System.currentTimeMillis();
        int units = allShops.size();
        int occupied = 0;
        int overdueCount = 0;
        Tenant next = null;
        for (Shop shop : allShops) {
            if (shop == null || shop.getShopId() == null) continue;
            Tenant tenant = tenantByShopId.get(shop.getShopId());
            if (shop.isOccupied() && tenant != null) {
                occupied++;
                if (DateCalculator.isOverdue(tenant.getDueDate(), now)) overdueCount++;
                if (DateCalculator.hasValidDueDate(tenant.getDueDate())
                        && (next == null || tenant.getDueDate() < next.getDueDate())) {
                    next = tenant;
                }
            }
        }
        int empty = units - occupied;

        tvSubtitle.setText(getString(R.string.dashboard_subtitle_format, units, occupied));
        tvStatUnits.setText(String.valueOf(units));
        tvStatOccupied.setText(String.valueOf(occupied));
        tvStatEmpty.setText(String.valueOf(empty));
        tvStatOverdue.setText(String.valueOf(overdueCount));

        if (next == null) {
            tvNextDue.setText(R.string.dashboard_no_upcoming);
        } else {
            boolean overdue = DateCalculator.isOverdue(next.getDueDate(), now);
            int days = DateCalculator.daysBetween(now, next.getDueDate());
            String date = DateCalculator.formatDdMmYyyy(next.getDueDate());
            tvNextDue.setText(overdue
                    ? getString(R.string.dashboard_next_due_overdue_format, next.getName(), date, Math.abs(days))
                    : getString(R.string.dashboard_next_due_upcoming_format, next.getName(), date, days));
        }
    }
}
