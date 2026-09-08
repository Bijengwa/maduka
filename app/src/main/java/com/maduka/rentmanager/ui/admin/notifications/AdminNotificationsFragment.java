package com.maduka.rentmanager.ui.admin.notifications;

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

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.NotifAdapter;
import com.maduka.rentmanager.ui.common.NotifItem;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Admin/Super Admin notifications: computed live from the full tenant portfolio
 * (TenantRepository.observeTenants - both roles are already authorized to see every tenant,
 * per the Shops/Tenants screens) rather than a persisted per-user notification store, since no
 * event-write pipeline exists this session (see design amendment doc for the deferred
 * OverdueCheckReceiver/NotificationRepository work). Only actionable states surface: overdue,
 * due today, due within 7 days - a comfortably-current tenant generates no card. */
public class AdminNotificationsFragment extends Fragment {
    private static final long DUE_SOON_WINDOW_DAYS = 7;
    private enum Filter { ALL, OVERDUE, DUE_TODAY, DUE_SOON }

    private final TenantRepository tenantRepository = new TenantRepository();
    private Filter filter = Filter.ALL;
    private List<NotifItem> allItems = new ArrayList<>();

    private RecyclerView recyclerNotifications;
    private TextView tvEmpty, tvSubtitle;
    private TextView filterAll, filterOverdue, filterDueToday, filterDueSoon;
    private NotifAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerNotifications = view.findViewById(R.id.recyclerNotifications);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        filterAll = view.findViewById(R.id.filterAll);
        filterOverdue = view.findViewById(R.id.filterOverdue);
        filterDueToday = view.findViewById(R.id.filterDueToday);
        filterDueSoon = view.findViewById(R.id.filterDueSoon);

        adapter = new NotifAdapter();
        recyclerNotifications.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerNotifications.setAdapter(adapter);

        filterAll.setOnClickListener(v -> setFilter(Filter.ALL));
        filterOverdue.setOnClickListener(v -> setFilter(Filter.OVERDUE));
        filterDueToday.setOnClickListener(v -> setFilter(Filter.DUE_TODAY));
        filterDueSoon.setOnClickListener(v -> setFilter(Filter.DUE_SOON));
        updateChipStyles();

        tenantRepository.observeTenants(new TenantRepository.TenantsListener() {
            @Override
            public void onTenants(List<Tenant> tenants) {
                if (!isAdded()) return;
                allItems = buildItems(tenants);
                render();
            }

            @Override
            public void onError(String message) { }
        });
    }

    private List<NotifItem> buildItems(List<Tenant> tenants) {
        long now = System.currentTimeMillis();
        List<NotifItem> items = new ArrayList<>();
        for (Tenant tenant : tenants) {
            int days = DateCalculator.daysBetween(now, tenant.getDueDate());
            String rent = String.format(Locale.US, "%,d", tenant.getMonthlyRent());
            String date = DateCalculator.formatDdMmYyyy(tenant.getDueDate());
            if (days < 0) {
                items.add(new NotifItem(NotifItem.Category.OVERDUE,
                        getString(R.string.notif_admin_overdue_title_format, tenant.getName()),
                        getString(R.string.notif_admin_overdue_message_format, rent, date, Math.abs(days)),
                        date, StatusPresentation.Tone.BAD, tenant.getDueDate()));
            } else if (days == 0) {
                items.add(new NotifItem(NotifItem.Category.DUE_TODAY,
                        getString(R.string.notif_admin_due_today_title_format, tenant.getName()),
                        getString(R.string.notif_admin_due_today_message_format, rent),
                        date, StatusPresentation.Tone.WAIT, tenant.getDueDate()));
            } else if (days <= DUE_SOON_WINDOW_DAYS) {
                items.add(new NotifItem(NotifItem.Category.DUE_SOON,
                        getString(R.string.notif_admin_due_soon_title_format, tenant.getName(), days),
                        getString(R.string.notif_admin_due_soon_message_format, rent, date),
                        date, StatusPresentation.Tone.WAIT, tenant.getDueDate()));
            }
        }
        items.sort((a, b) -> Long.compare(a.sortKey, b.sortKey));
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
        style(filterDueToday, filter == Filter.DUE_TODAY);
        style(filterDueSoon, filter == Filter.DUE_SOON);
    }

    private void style(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.shape_pill_selected : R.drawable.shape_pill_unselected);
        chip.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(),
                selected ? R.color.md_accent_300 : R.color.md_neutral_500));
    }

    private void render() {
        List<NotifItem> filtered = new ArrayList<>();
        for (NotifItem item : allItems) {
            boolean matches;
            switch (filter) {
                case OVERDUE: matches = item.category == NotifItem.Category.OVERDUE; break;
                case DUE_TODAY: matches = item.category == NotifItem.Category.DUE_TODAY; break;
                case DUE_SOON: matches = item.category == NotifItem.Category.DUE_SOON; break;
                default: matches = true;
            }
            if (matches) filtered.add(item);
        }
        adapter.submitList(filtered);
        tvSubtitle.setText(getString(R.string.notifications_subtitle_format, allItems.size()));
        recyclerNotifications.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
}
