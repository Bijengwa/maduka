package com.maduka.rentmanager.ui.tenant.notifications;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.NotifAdapter;
import com.maduka.rentmanager.ui.common.NotifItem;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Tenant's own notifications: computed live from their own Tenant record (due/overdue state,
 * via observeTenant(uid)) and their own payment records (confirmed/rejected, via
 * observePaymentsForTenant(uid)) - strictly personal, never another tenant's data. */
public class TenantNotificationsFragment extends Fragment {
    private enum Filter { ALL, OVERDUE, DUE_SOON, CONFIRMED }

    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();

    private Filter filter = Filter.ALL;
    private NotifItem dueItem;
    private List<NotifItem> paymentItems = new ArrayList<>();
    private String shopName = "";

    private RecyclerView recyclerNotifications;
    private TextView tvEmpty, tvSubtitle;
    private TextView filterAll, filterOverdue, filterDueSoon, filterConfirmed;
    private NotifAdapter adapter;
    private String tenantUid, tenantShopId;
    private ValueEventListener tenantRegistration, shopRegistration, paymentsRegistration;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenant_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerNotifications = view.findViewById(R.id.recyclerNotifications);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        filterAll = view.findViewById(R.id.filterAll);
        filterOverdue = view.findViewById(R.id.filterOverdue);
        filterDueSoon = view.findViewById(R.id.filterDueSoon);
        filterConfirmed = view.findViewById(R.id.filterConfirmed);

        adapter = new NotifAdapter();
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerNotifications.setAdapter(adapter);

        filterAll.setOnClickListener(v -> setFilter(Filter.ALL));
        filterOverdue.setOnClickListener(v -> setFilter(Filter.OVERDUE));
        filterDueSoon.setOnClickListener(v -> setFilter(Filter.DUE_SOON));
        filterConfirmed.setOnClickListener(v -> setFilter(Filter.CONFIRMED));
        updateChipStyles();

        tenantUid = new AuthRepository().currentUid();
        if (tenantUid == null) return;

        tenantRegistration = tenantRepository.observeTenant(tenantUid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (!isAdded() || tenant == null) return;
                tenantShopId = tenant.getShopId();
                shopRegistration = shopRepository.observeShop(tenant.getShopId(), new ShopRepository.ShopListener() {
                    @Override
                    public void onShop(Shop shop) {
                        if (!isAdded()) return;
                        shopName = shop != null && shop.getName() != null ? shop.getName() : tenant.getShopId();
                        dueItem = buildDueItem(tenant);
                        render();
                    }

                    @Override
                    public void onError(String message) { }
                });
            }

            @Override
            public void onError(String message) { }
        });

        paymentsRegistration = paymentRepository.observePaymentsForTenant(tenantUid, new PaymentRepository.PaymentsListener() {
            @Override
            public void onPayments(List<PaymentRecord> payments) {
                if (!isAdded()) return;
                paymentItems = buildPaymentItems(payments);
                render();
            }

            @Override
            public void onError(String message) { }
        });
    }

    @Override
    public void onDestroyView() {
        if (tenantRegistration != null) tenantRepository.stopObservingTenant(tenantUid, tenantRegistration);
        if (shopRegistration != null) shopRepository.stopObservingShop(tenantShopId, shopRegistration);
        if (paymentsRegistration != null) paymentRepository.stopObservingPaymentsForTenant(tenantUid, paymentsRegistration);
        tenantRegistration = null;
        shopRegistration = null;
        paymentsRegistration = null;
        super.onDestroyView();
    }

    private NotifItem buildDueItem(Tenant tenant) {
        if (!DateCalculator.hasValidDueDate(tenant.getDueDate())) return null;
        long now = System.currentTimeMillis();
        int days = DateCalculator.daysBetween(now, tenant.getDueDate());
        if (days < 0) {
            return new NotifItem(NotifItem.Category.OVERDUE,
                    getString(R.string.notif_tenant_overdue_title),
                    getString(R.string.notif_tenant_overdue_message_format, shopName, Math.abs(days)),
                    DateCalculator.formatDdMmYyyy(tenant.getDueDate()), StatusPresentation.Tone.BAD, tenant.getDueDate());
        } else if (days == 0) {
            return new NotifItem(NotifItem.Category.DUE_TODAY,
                    getString(R.string.notif_tenant_due_today_title),
                    getString(R.string.notif_tenant_due_today_message_format,
                            String.format(Locale.US, "%,d", tenant.getMonthlyRent())),
                    DateCalculator.formatDdMmYyyy(tenant.getDueDate()), StatusPresentation.Tone.WAIT, tenant.getDueDate());
        } else if (days <= 7) {
            return new NotifItem(NotifItem.Category.DUE_SOON,
                    getString(R.string.notif_tenant_due_soon_title),
                    getString(R.string.notif_tenant_due_soon_message_format, shopName, days),
                    DateCalculator.formatDdMmYyyy(tenant.getDueDate()), StatusPresentation.Tone.WAIT, tenant.getDueDate());
        }
        return null;
    }

    private List<NotifItem> buildPaymentItems(List<PaymentRecord> payments) {
        List<NotifItem> items = new ArrayList<>();
        for (PaymentRecord p : payments) {
            String date = DateCalculator.formatDdMmYyyy(p.getPaymentDate());
            items.add(new NotifItem(NotifItem.Category.CONFIRMED,
                    getString(R.string.notif_tenant_confirmed_title),
                    getString(R.string.notif_tenant_confirmed_message_format,
                            String.format(Locale.US, "%,d", p.getAmount()), p.getMonthsCovered()),
                    date, StatusPresentation.Tone.GOOD, p.getPaymentDate()));
        }
        return items;
    }

    private void setFilter(Filter newFilter) {
        filter = newFilter;
        updateChipStyles();
        render();
    }

    private void updateChipStyles() {
        style(filterAll, filter == Filter.ALL);
        style(filterOverdue, filter == Filter.OVERDUE);
        style(filterDueSoon, filter == Filter.DUE_SOON);
        style(filterConfirmed, filter == Filter.CONFIRMED);
    }

    private void style(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.shape_pill_selected : R.drawable.shape_pill_unselected);
        chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                selected ? R.color.md_bg : R.color.md_text));
    }

    private void render() {
        List<NotifItem> all = new ArrayList<>();
        if (dueItem != null) all.add(dueItem);
        all.addAll(paymentItems);
        all.sort((a, b) -> Long.compare(b.sortKey, a.sortKey));

        List<NotifItem> filtered = new ArrayList<>();
        for (NotifItem item : all) {
            boolean matches;
            switch (filter) {
                case OVERDUE: matches = item.category == NotifItem.Category.OVERDUE; break;
                case DUE_SOON: matches = item.category == NotifItem.Category.DUE_SOON || item.category == NotifItem.Category.DUE_TODAY; break;
                case CONFIRMED: matches = item.category == NotifItem.Category.CONFIRMED; break;
                default: matches = true;
            }
            if (matches) filtered.add(item);
        }
        adapter.submitList(filtered);
        tvSubtitle.setText(getString(R.string.notifications_subtitle_format, all.size()));
        recyclerNotifications.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
