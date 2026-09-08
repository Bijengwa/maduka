package com.maduka.rentmanager.ui.admin.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.admin.payments.RecordPaymentActivity;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.MoneyFormatter;
import com.maduka.rentmanager.util.Prefs;
import com.maduka.rentmanager.util.VerseProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Admin/Super Admin dashboard: welcome + Bible verse (Admin/Super Admin only, never Tenant) +
 * live summary cards + upcoming payments + a sticky payment ledger with Zote/Inakaribia/Hai/
 * Imechelewa filters. Both roles share this information architecture; only Record Payment is
 * gated to Super Admin (Admin is read-only everywhere per the product rules). There is no
 * payment approval workflow - every PaymentRecord shown here is already a valid recorded
 * payment. Former tenants (Tenant.shopId cleared by TenantRepository.endTenancy) never appear:
 * every join here goes through the currently-active tenant map, never the raw tenant list. */
public class AdminDashboardFragment extends Fragment {
    private static final String ARG_ROLE = "arg_role";
    private static final int MAX_UPCOMING_ROWS = 5;

    private enum PaymentFilter { ALL, NEXT_DUE, ACTIVE, OVERDUE }

    private final ShopRepository shopRepository = new ShopRepository();
    private final TenantRepository tenantRepository = new TenantRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private final UserRepository userRepository = new UserRepository();

    private UserRole role;
    private List<Shop> allShops = new ArrayList<>();
    private List<Tenant> allTenants = new ArrayList<>();
    private List<PaymentRecord> allPayments = new ArrayList<>();
    private boolean shopsLoaded, tenantsLoaded, paymentsLoaded;

    private Map<String, Tenant> activeTenantsByUid = new HashMap<>();
    private Map<String, Tenant> activeTenantByShopId = new HashMap<>();
    private Map<String, String> shopNamesById = new HashMap<>();
    private PaymentFilter currentFilter = PaymentFilter.ALL;
    private long collectionThisMonth;
    private boolean collectionAmountExpanded;

    private AppBarLayout appBar;
    private TextView tvWelcome, tvWelcomeSubtitle;
    private View btnRecordPayment;
    private TextView tvVerseText, tvVerseReference;
    private View cardUnits, cardCollection, cardNextDue, cardOverdue;
    private TextView tvStatUnits, tvStatUnitsNote, tvStatCollection, tvStatNextDue, tvStatOverdue, tvStatOverdueNote;
    private TextView tvViewAllUpcoming, tvUpcomingEmpty;
    private RecyclerView recyclerUpcoming;
    private UpcomingPaymentAdapter upcomingAdapter;
    private View btnRefresh;
    private TextView chipAll, chipNextDue, chipActive, chipOverdue;
    private RecyclerView recyclerPayments;
    private TextView tvPaymentsEmpty;
    private DashboardPaymentAdapter paymentAdapter;

    private ValueEventListener shopsRegistration, tenantsRegistration, paymentsRegistration;

    public static AdminDashboardFragment newInstance(UserRole role) {
        AdminDashboardFragment fragment = new AdminDashboardFragment();
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
        return inflater.inflate(R.layout.fragment_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        appBar = view.findViewById(R.id.appBar);
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvWelcomeSubtitle = view.findViewById(R.id.tvWelcomeSubtitle);
        btnRecordPayment = view.findViewById(R.id.btnRecordPayment);
        tvVerseText = view.findViewById(R.id.tvVerseText);
        tvVerseReference = view.findViewById(R.id.tvVerseReference);
        cardUnits = view.findViewById(R.id.cardUnits);
        cardCollection = view.findViewById(R.id.cardCollection);
        cardNextDue = view.findViewById(R.id.cardNextDue);
        cardOverdue = view.findViewById(R.id.cardOverdue);
        tvStatUnits = view.findViewById(R.id.tvStatUnits);
        tvStatUnitsNote = view.findViewById(R.id.tvStatUnitsNote);
        tvStatCollection = view.findViewById(R.id.tvStatCollection);
        tvStatNextDue = view.findViewById(R.id.tvStatNextDue);
        tvStatOverdue = view.findViewById(R.id.tvStatOverdue);
        tvStatOverdueNote = view.findViewById(R.id.tvStatOverdueNote);
        tvViewAllUpcoming = view.findViewById(R.id.tvViewAllUpcoming);
        tvUpcomingEmpty = view.findViewById(R.id.tvUpcomingEmpty);
        recyclerUpcoming = view.findViewById(R.id.recyclerUpcoming);
        btnRefresh = view.findViewById(R.id.btnRefresh);
        chipAll = view.findViewById(R.id.chipAll);
        chipNextDue = view.findViewById(R.id.chipNextDue);
        chipActive = view.findViewById(R.id.chipActive);
        chipOverdue = view.findViewById(R.id.chipOverdue);
        recyclerPayments = view.findViewById(R.id.recyclerPayments);
        tvPaymentsEmpty = view.findViewById(R.id.tvPaymentsEmpty);

        upcomingAdapter = new UpcomingPaymentAdapter();
        recyclerUpcoming.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerUpcoming.setAdapter(upcomingAdapter);

        paymentAdapter = new DashboardPaymentAdapter();
        recyclerPayments.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerPayments.setAdapter(paymentAdapter);

        Prefs prefs = Prefs.get(requireContext());
        int verseIndex = VerseProvider.indexFor(prefs.signInCount());
        String[] verseTexts = getResources().getStringArray(R.array.verse_texts);
        String[] verseReferences = getResources().getStringArray(R.array.verse_references);
        tvVerseText.setText("“" + verseTexts[verseIndex] + "”");
        tvVerseReference.setText("— " + verseReferences[verseIndex]);

        // Only Super Admin may create/manage operational data - Admin is read-only everywhere,
        // so Record Payment simply never becomes visible for that role.
        if (role == UserRole.SUPER_ADMIN) {
            btnRecordPayment.setVisibility(View.VISIBLE);
            btnRecordPayment.setOnClickListener(v -> {
                Intent intent = new Intent(getContext(), RecordPaymentActivity.class);
                intent.putExtra(RecordPaymentActivity.EXTRA_ROLE, role.name());
                startActivity(intent);
            });
        }

        String uid = new AuthRepository().currentUid();
        userRepository.observeCurrentUserName(role, uid, name -> {
            if (!canTouchViews()) return;
            tvWelcome.setText(name != null && !name.isEmpty()
                    ? getString(R.string.dashboard_welcome_named_format, name)
                    : getString(R.string.dashboard_welcome_generic));
        });

        cardUnits.setOnClickListener(v -> switchTab(R.id.nav_properties));
        cardCollection.setOnClickListener(v -> {
            collectionAmountExpanded = !collectionAmountExpanded;
            updateCollectionDisplay();
        });
        cardNextDue.setOnClickListener(v -> jumpToPayments(PaymentFilter.NEXT_DUE));
        cardOverdue.setOnClickListener(v -> jumpToPayments(PaymentFilter.OVERDUE));
        tvViewAllUpcoming.setOnClickListener(v -> jumpToPayments(PaymentFilter.NEXT_DUE));
        btnRefresh.setOnClickListener(v -> refreshData());
        chipAll.setOnClickListener(v -> setFilter(PaymentFilter.ALL));
        chipNextDue.setOnClickListener(v -> setFilter(PaymentFilter.NEXT_DUE));
        chipActive.setOnClickListener(v -> setFilter(PaymentFilter.ACTIVE));
        chipOverdue.setOnClickListener(v -> setFilter(PaymentFilter.OVERDUE));
        updateChipStyles();

        attachListeners();
    }

    @Override
    public void onDestroyView() {
        detachListeners();
        super.onDestroyView();
    }

    private boolean canTouchViews() {
        return isAdded() && getView() != null;
    }

    private void attachListeners() {
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

    private void detachListeners() {
        if (shopsRegistration != null) shopRepository.stopObservingShops(shopsRegistration);
        if (tenantsRegistration != null) tenantRepository.stopObservingTenants(tenantsRegistration);
        if (paymentsRegistration != null) paymentRepository.stopObservingPayments(paymentsRegistration);
        shopsRegistration = null;
        tenantsRegistration = null;
        paymentsRegistration = null;
    }

    /** Refresh is a real reload, not decorative: detach and re-attach every listener so Firebase
     * re-delivers the current synced value immediately. */
    private void refreshData() {
        detachListeners();
        attachListeners();
    }

    private void switchTab(int navId) {
        View bottomNav = requireActivity().findViewById(R.id.bottomNav);
        if (bottomNav instanceof BottomNavigationView) {
            ((BottomNavigationView) bottomNav).setSelectedItemId(navId);
        }
    }

    private void jumpToPayments(PaymentFilter filter) {
        setFilter(filter);
        if (appBar != null) appBar.setExpanded(false, true);
    }

    private void setFilter(PaymentFilter filter) {
        currentFilter = filter;
        updateChipStyles();
        renderPayments();
    }

    private void updateChipStyles() {
        style(chipAll, currentFilter == PaymentFilter.ALL);
        style(chipNextDue, currentFilter == PaymentFilter.NEXT_DUE);
        style(chipActive, currentFilter == PaymentFilter.ACTIVE);
        style(chipOverdue, currentFilter == PaymentFilter.OVERDUE);
    }

    private void style(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.shape_pill_selected : R.drawable.shape_pill_unselected);
        chip.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.md_accent_300 : R.color.md_neutral_500));
    }

    private String shopNameFor(String shopId) {
        String name = shopNamesById.get(shopId);
        return name != null ? name : shopId;
    }

    private void render() {
        shopNamesById = new HashMap<>();
        for (Shop shop : allShops) {
            if (shop != null && shop.getShopId() != null) {
                shopNamesById.put(shop.getShopId(), shop.getName() != null ? shop.getName() : shop.getShopId());
            }
        }

        activeTenantsByUid = new HashMap<>();
        activeTenantByShopId = new HashMap<>();
        for (Tenant t : allTenants) {
            if (t != null && t.getShopId() != null && !t.getShopId().isEmpty()) {
                activeTenantsByUid.put(t.getUid(), t);
                activeTenantByShopId.put(t.getShopId(), t);
            }
        }

        if (shopsLoaded && tenantsLoaded) {
            renderStats();
            renderUpcoming();
        }
        if (shopsLoaded && tenantsLoaded && paymentsLoaded) {
            renderPayments();
        }
    }

    private void renderStats() {
        long now = System.currentTimeMillis();

        int units = allShops.size();
        int occupied = 0;
        for (Shop shop : allShops) {
            if (shop != null && shop.isOccupied() && shop.getShopId() != null
                    && activeTenantByShopId.containsKey(shop.getShopId())) {
                occupied++;
            }
        }

        int nextDueCount = 0, overdueCount = 0;
        long overdueAmount = 0;
        for (Tenant t : activeTenantsByUid.values()) {
            DateCalculator.DueBucket bucket = DateCalculator.classifyDueDate(t.getDueDate(), now);
            if (bucket == DateCalculator.DueBucket.NEXT_DUE) {
                nextDueCount++;
            } else if (bucket == DateCalculator.DueBucket.OVERDUE) {
                overdueCount++;
                overdueAmount += t.getMonthlyRent();
            }
        }

        long monthStart = DateCalculator.startOfMonth(now);
        long nextMonthStart = DateCalculator.addMonths(monthStart, 1);
        collectionThisMonth = 0;
        for (PaymentRecord p : allPayments) {
            if (p != null && p.getPaymentDate() >= monthStart && p.getPaymentDate() < nextMonthStart) {
                collectionThisMonth += p.getAmount();
            }
        }

        tvStatUnits.setText(String.valueOf(units));
        tvStatUnitsNote.setText(getString(R.string.dashboard_units_note_format, occupied, units));
        updateCollectionDisplay();
        tvStatNextDue.setText(String.valueOf(nextDueCount));
        tvStatOverdue.setText(String.valueOf(overdueCount));
        tvStatOverdueNote.setText(getString(R.string.dashboard_amount_format, MoneyFormatter.compact(overdueAmount)));
    }

    /** Compact by default so a large amount never breaks the card's layout; tapping the card
     * (see the click listener in onViewCreated) reveals the exact figure, per the design spec. */
    private void updateCollectionDisplay() {
        String amount = collectionAmountExpanded
                ? MoneyFormatter.full(collectionThisMonth)
                : MoneyFormatter.compact(collectionThisMonth);
        tvStatCollection.setText(getString(R.string.dashboard_amount_format, amount));
    }

    private void renderUpcoming() {
        long now = System.currentTimeMillis();
        List<Tenant> upcoming = new ArrayList<>();
        for (Tenant t : activeTenantsByUid.values()) {
            if (DateCalculator.classifyDueDate(t.getDueDate(), now) == DateCalculator.DueBucket.NEXT_DUE) {
                upcoming.add(t);
            }
        }
        upcoming.sort((a, b) -> Long.compare(a.getDueDate(), b.getDueDate()));

        List<UpcomingPaymentAdapter.Row> rows = new ArrayList<>();
        for (int i = 0; i < upcoming.size() && i < MAX_UPCOMING_ROWS; i++) {
            Tenant t = upcoming.get(i);
            rows.add(new UpcomingPaymentAdapter.Row(t, shopNameFor(t.getShopId())));
        }
        upcomingAdapter.submitList(rows);

        boolean empty = upcoming.isEmpty();
        recyclerUpcoming.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvUpcomingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        tvViewAllUpcoming.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void renderPayments() {
        long now = System.currentTimeMillis();
        List<PaymentRecord> sorted = new ArrayList<>(allPayments);
        sorted.sort((a, b) -> Long.compare(b.getPaymentDate(), a.getPaymentDate()));

        List<DashboardPaymentAdapter.Row> rows = new ArrayList<>();
        for (PaymentRecord p : sorted) {
            if (p == null || p.getTenantUid() == null) continue;
            // Former tenants (Tenant.shopId cleared) never appear on the active dashboard -
            // their history stays available through Reports/tenant history instead.
            Tenant tenant = activeTenantsByUid.get(p.getTenantUid());
            if (tenant == null) continue;
            DateCalculator.DueBucket bucket = DateCalculator.classifyDueDate(tenant.getDueDate(), now);
            if (!matchesFilter(bucket)) continue;
            rows.add(new DashboardPaymentAdapter.Row(p, tenant.getName(), shopNameFor(tenant.getShopId()), bucket,
                    tenant.getDueDate()));
        }
        paymentAdapter.submitList(rows);

        boolean empty = rows.isEmpty();
        recyclerPayments.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvPaymentsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilter(DateCalculator.DueBucket bucket) {
        switch (currentFilter) {
            case NEXT_DUE: return bucket == DateCalculator.DueBucket.NEXT_DUE;
            case ACTIVE: return bucket == DateCalculator.DueBucket.ACTIVE;
            case OVERDUE: return bucket == DateCalculator.DueBucket.OVERDUE;
            default: return true;
        }
    }
}
