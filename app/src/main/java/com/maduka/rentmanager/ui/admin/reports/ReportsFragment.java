package com.maduka.rentmanager.ui.admin.reports;

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
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only Reports screen for Admin and Super Admin - every figure is computed live from the
 * same Shop/Tenant/PaymentRecord data the rest of the app uses, never hardcoded. If a real
 * project has zero payment records, the payments section shows real zeros plus a plain "no
 * records yet" note rather than fabricating numbers. */
public class ReportsFragment extends Fragment {
    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();

    private List<Shop> allShops = new ArrayList<>();
    private List<Tenant> allTenants = new ArrayList<>();
    private List<PaymentRecord> allPayments = new ArrayList<>();
    private boolean shopsLoaded, tenantsLoaded, paymentsLoaded;

    private Row rowTotalShops, rowOccupied, rowVacant, rowOccupancy, rowActiveTenants, rowOverdueTenants;
    private Row rowTotalRecords, rowTotalAmount, rowAverageAmount;
    private TextView tvNoPayments;

    private ValueEventListener shopsRegistration, tenantsRegistration, paymentsRegistration;

    private static class Row {
        final TextView label, value;
        Row(View root) {
            label = root.findViewById(R.id.tvLabel);
            value = root.findViewById(R.id.tvValue);
        }
        void set(String labelText, String valueText) {
            label.setText(labelText);
            value.setText(valueText);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reports, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rowTotalShops = new Row(view.findViewById(R.id.rowTotalShops));
        rowOccupied = new Row(view.findViewById(R.id.rowOccupied));
        rowVacant = new Row(view.findViewById(R.id.rowVacant));
        rowOccupancy = new Row(view.findViewById(R.id.rowOccupancy));
        rowActiveTenants = new Row(view.findViewById(R.id.rowActiveTenants));
        rowOverdueTenants = new Row(view.findViewById(R.id.rowOverdueTenants));
        rowTotalRecords = new Row(view.findViewById(R.id.rowTotalRecords));
        rowTotalAmount = new Row(view.findViewById(R.id.rowTotalAmount));
        rowAverageAmount = new Row(view.findViewById(R.id.rowAverageAmount));
        tvNoPayments = view.findViewById(R.id.tvNoPayments);

        shopsRegistration = shopRepository.observeShops(new ShopRepository.ShopsListener() {
            @Override
            public void onShops(List<Shop> shops) {
                if (!canTouchViews()) return;
                allShops = shops != null ? shops : new ArrayList<>();
                shopsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) { }
        });

        tenantsRegistration = tenantRepository.observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                if (!canTouchViews()) return;
                allTenants = tenants != null ? tenants : new ArrayList<>();
                tenantsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) { }
        });

        paymentsRegistration = paymentRepository.observePayments(new PaymentRepository.PaymentsListener() {
            @Override
            public void onPayments(List<PaymentRecord> payments) {
                if (!canTouchViews()) return;
                allPayments = payments != null ? payments : new ArrayList<>();
                paymentsLoaded = true;
                render();
            }

            @Override
            public void onError(String message) { }
        });
    }

    @Override
    public void onDestroyView() {
        if (shopsRegistration != null) shopRepository.stopObservingShops(shopsRegistration);
        if (tenantsRegistration != null) tenantRepository.stopObservingTenants(tenantsRegistration);
        if (paymentsRegistration != null) paymentRepository.stopObservingPayments(paymentsRegistration);
        shopsRegistration = null;
        tenantsRegistration = null;
        paymentsRegistration = null;
        super.onDestroyView();
    }

    private boolean canTouchViews() {
        return isAdded() && getView() != null;
    }

    private void render() {
        if (shopsLoaded && tenantsLoaded) renderPortfolio();
        if (paymentsLoaded) renderPayments();
    }

    private void renderPortfolio() {
        int total = allShops.size();
        int occupied = 0;
        for (Shop s : allShops) {
            if (s != null && s.isOccupied()) occupied++;
        }
        int vacant = total - occupied;
        int occupancyPct = total > 0 ? Math.round(occupied * 100f / total) : 0;

        long now = System.currentTimeMillis();
        int overdue = 0;
        for (Tenant t : allTenants) {
            if (t != null && DateCalculator.isOverdue(t.getDueDate(), now)) overdue++;
        }

        rowTotalShops.set(getString(R.string.reports_total_shops), String.valueOf(total));
        rowOccupied.set(getString(R.string.reports_occupied_shops), String.valueOf(occupied));
        rowVacant.set(getString(R.string.reports_vacant_shops), String.valueOf(vacant));
        rowOccupancy.set(getString(R.string.reports_occupancy_rate), occupancyPct + "%");
        rowActiveTenants.set(getString(R.string.reports_active_tenants), String.valueOf(allTenants.size()));
        rowOverdueTenants.set(getString(R.string.reports_overdue_tenants), String.valueOf(overdue));
    }

    private void renderPayments() {
        long totalAmount = 0;
        for (PaymentRecord p : allPayments) {
            if (p == null) continue;
            totalAmount += p.getAmount();
        }
        long averageAmount = allPayments.isEmpty() ? 0 : totalAmount / allPayments.size();

        rowTotalRecords.set(getString(R.string.reports_total_records), String.valueOf(allPayments.size()));
        rowTotalAmount.set(getString(R.string.reports_total_amount),
                String.format(Locale.US, "TSh %,d", totalAmount));
        rowAverageAmount.set(getString(R.string.reports_average_amount),
                String.format(Locale.US, "TSh %,d", averageAmount));

        tvNoPayments.setVisibility(allPayments.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
