package com.maduka.rentmanager.ui.tenant.dashboard;

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
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.StatusPill;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

/** Tenant's own home screen: retrieves only the signed-in tenant's own record via
 * TenantRepository.observeTenant(uid), never the full tenants list. No management-wide
 * metrics, no KJV verse (Tenant side never shows it, per Global Constraints). */
public class TenantDashboardFragment extends Fragment {
    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();

    private TextView tvSubtitle, tvWelcome, tvLastPayment, tvDueDate, tvStatus, tvDaysMessage;
    private String tenantUid, tenantShopId;
    private ValueEventListener tenantRegistration, shopRegistration;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenant_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvWelcome = view.findViewById(R.id.tvWelcome);
        tvLastPayment = view.findViewById(R.id.tvLastPayment);
        tvDueDate = view.findViewById(R.id.tvDueDate);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvDaysMessage = view.findViewById(R.id.tvDaysMessage);

        View rowHistory = view.findViewById(R.id.rowHistory);
        View rowDetails = view.findViewById(R.id.rowDetails);
        rowHistory.setOnClickListener(v -> navigateTo(new TenantHistoryFragment()));
        rowDetails.setOnClickListener(v -> navigateTo(new TenantDetailsFragment()));

        tenantUid = new AuthRepository().currentUid();
        if (tenantUid == null) return;
        tenantRegistration = tenantRepository.observeTenant(tenantUid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (!isAdded() || tenant == null) return;
                bind(tenant);
            }

            @Override
            public void onError(String message) { }
        });
    }

    @Override
    public void onDestroyView() {
        if (tenantRegistration != null) tenantRepository.stopObservingTenant(tenantUid, tenantRegistration);
        if (shopRegistration != null) shopRepository.stopObservingShop(tenantShopId, shopRegistration);
        tenantRegistration = null;
        shopRegistration = null;
        super.onDestroyView();
    }

    private void bind(Tenant tenant) {
        tvWelcome.setText(getString(R.string.login_sign_in_as) + ", " + firstName(tenant.getName()));
        tenantShopId = tenant.getShopId();
        shopRegistration = shopRepository.observeShop(tenant.getShopId(), new ShopRepository.ShopListener() {
            @Override
            public void onShop(Shop shop) {
                if (!isAdded() || shop == null) return;
                tvSubtitle.setText(shop.getName() != null ? shop.getName() : tenant.getShopId());
            }

            @Override
            public void onError(String message) { }
        });

        tvLastPayment.setText(DateCalculator.formatDdMmYyyy(tenant.getLastPaymentDate()));
        tvDueDate.setText(DateCalculator.formatDdMmYyyy(tenant.getDueDate()));

        long now = System.currentTimeMillis();
        boolean overdue = DateCalculator.isOverdue(tenant.getDueDate(), now);
        int days = DateCalculator.daysBetween(now, tenant.getDueDate());
        PresenceStatus presence = tenant.getPresenceStatus() != null ? tenant.getPresenceStatus() : PresenceStatus.YUPO;

        StatusPresentation.Tone tone;
        int labelRes;
        if (overdue) {
            tone = StatusPresentation.Tone.BAD;
            labelRes = R.string.status_overdue;
        } else if (presence == PresenceStatus.YUPO) {
            tone = StatusPresentation.Tone.GOOD;
            labelRes = R.string.status_active;
        } else {
            tone = StatusPresentation.Tone.BAD;
            labelRes = R.string.status_disabled;
        }
        StatusPill.apply(tvStatus, tone, labelRes);
        tvDaysMessage.setText(overdue
                ? getString(R.string.tenant_days_overdue_message, Math.abs(days))
                : getString(R.string.tenant_days_left_message, days));
    }

    private String firstName(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) return "";
        String trimmed = fullName.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private void navigateTo(Fragment fragment) {
        if (getParentFragmentManager().isStateSaved()) return;
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
