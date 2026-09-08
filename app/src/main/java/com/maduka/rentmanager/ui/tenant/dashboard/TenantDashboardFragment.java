package com.maduka.rentmanager.ui.tenant.dashboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.maduka.rentmanager.ui.common.MadukaToast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.MoneyFormatter;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.List;
import java.util.Locale;

/** Tenant's own home screen: retrieves only the signed-in tenant's own record via
 * TenantRepository.observeTenant(uid) and their own payment history via
 * PaymentRepository.observePaymentsForTenant(uid) - never the full tenants/payments list. No
 * management-wide metrics, no KJV verse (Tenant side never shows it, per Global Constraints).
 * Total Paid/Months Covered are summed from the real payment history rather than read off a
 * single cached field, so they stay correct across renewals without any extra bookkeeping. */
public class TenantDashboardFragment extends Fragment {
    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();

    private TextView tvSubtitle, tvWelcome;
    private TextView tvTotalPaid, tvMonthsCovered, tvLastPayment, tvLastPaymentAmount, tvDueDate, tvDueDays;
    private ImageView ivStatus;
    private TextView tvStatusLabel, tvStatusMessage;
    private View rowHistory, rowDetails, rowContactAdmin;

    private String tenantUid, tenantShopId;
    private ValueEventListener tenantRegistration, shopRegistration, paymentsRegistration;

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
        tvTotalPaid = view.findViewById(R.id.tvTotalPaid);
        tvMonthsCovered = view.findViewById(R.id.tvMonthsCovered);
        tvLastPayment = view.findViewById(R.id.tvLastPayment);
        tvLastPaymentAmount = view.findViewById(R.id.tvLastPaymentAmount);
        tvDueDate = view.findViewById(R.id.tvDueDate);
        tvDueDays = view.findViewById(R.id.tvDueDays);
        ivStatus = view.findViewById(R.id.ivStatus);
        tvStatusLabel = view.findViewById(R.id.tvStatusLabel);
        tvStatusMessage = view.findViewById(R.id.tvStatusMessage);
        rowHistory = view.findViewById(R.id.rowHistory);
        rowDetails = view.findViewById(R.id.rowDetails);
        rowContactAdmin = view.findViewById(R.id.rowContactAdmin);

        rowHistory.setOnClickListener(v -> navigateTo(new TenantHistoryFragment()));
        rowDetails.setOnClickListener(v -> navigateTo(new TenantDetailsFragment()));
        // No admin-contact mechanism exists yet: a Tenant's Firebase read access does not extend
        // to the admins/super_admins nodes (by design, per the security rules), and there is no
        // per-tenant "assigned admin" record to look up a real number from. Telling the user
        // honestly beats faking a phone number or a dead action.
        rowContactAdmin.setOnClickListener(v ->
                MadukaToast.show(requireActivity(), getString(R.string.tenant_contact_not_available), MadukaToast.Kind.INFO));

        tenantUid = new AuthRepository().currentUid();
        if (tenantUid == null) return;
        tenantRegistration = tenantRepository.observeTenant(tenantUid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (!isAdded() || tenant == null) return;
                bindTenant(tenant);
            }

            @Override
            public void onError(String message) { }
        });

        paymentsRegistration = paymentRepository.observePaymentsForTenant(tenantUid, new PaymentRepository.PaymentsListener() {
            @Override
            public void onPayments(List<PaymentRecord> payments) {
                if (!isAdded()) return;
                bindPayments(payments);
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

    private void bindTenant(Tenant tenant) {
        tvWelcome.setText(tenant.getName() != null && !tenant.getName().isEmpty()
                ? getString(R.string.dashboard_welcome_named_format, firstName(tenant.getName()))
                : getString(R.string.dashboard_welcome_generic));

        boolean active = tenant.getShopId() != null && !tenant.getShopId().isEmpty();
        tenantShopId = tenant.getShopId();
        if (active) {
            shopRegistration = shopRepository.observeShop(tenant.getShopId(), new ShopRepository.ShopListener() {
                @Override
                public void onShop(Shop shop) {
                    if (!isAdded() || shop == null) return;
                    // Shop has no location field in the current model - showing only the real
                    // name rather than fabricating a district/area.
                    tvSubtitle.setText(shop.getName() != null ? shop.getName() : tenant.getShopId());
                }

                @Override
                public void onError(String message) { }
            });
        } else {
            tvSubtitle.setText("");
        }

        String notSet = getString(R.string.label_date_not_set);
        tvDueDate.setText(DateCalculator.formatDdMmOrUnknown(tenant.getDueDate(), notSet));

        long now = System.currentTimeMillis();
        if (!active) {
            tvDueDays.setText("");
            renderEmptyStatus();
        } else if (!DateCalculator.hasValidDueDate(tenant.getDueDate())) {
            tvDueDays.setText("");
            renderEmptyStatus();
        } else {
            int days = DateCalculator.daysBetween(now, tenant.getDueDate());
            boolean overdue = days < 0;
            tvDueDays.setText(overdue
                    ? getString(R.string.label_days_overdue_format, Math.abs(days))
                    : getString(R.string.label_days_left_format, days));
            DateCalculator.DueBucket bucket = DateCalculator.classifyDueDate(tenant.getDueDate(), now);
            renderStatus(bucket, tenant.getDueDate(), now);
        }
    }

    private void bindPayments(List<PaymentRecord> payments) {
        long totalPaid = 0;
        int monthsCovered = 0;
        PaymentRecord latest = null;
        for (PaymentRecord p : payments) {
            if (p == null) continue;
            totalPaid += p.getAmount();
            monthsCovered += p.getMonthsCovered();
            if (latest == null || p.getPaymentDate() > latest.getPaymentDate()) latest = p;
        }

        tvTotalPaid.setText(MoneyFormatter.compact(totalPaid));
        tvMonthsCovered.setText(String.valueOf(monthsCovered));

        if (latest != null) {
            String notSet = getString(R.string.label_date_not_set);
            tvLastPayment.setText(DateCalculator.formatDdMmOrUnknown(latest.getPaymentDate(), notSet));
            tvLastPaymentAmount.setText(getString(R.string.dashboard_amount_format,
                    String.format(Locale.US, "%,d", latest.getAmount())));
        } else {
            tvLastPayment.setText(getString(R.string.label_date_not_set));
            tvLastPaymentAmount.setText("");
        }
    }

    private void renderStatus(DateCalculator.DueBucket bucket, long dueDate, long now) {
        StatusPresentation.Tone tone = StatusPresentation.toneFor(bucket);
        int labelRes;
        int iconRes;
        String message;
        switch (bucket) {
            case OVERDUE: {
                labelRes = R.string.tenant_status_overdue_label;
                iconRes = R.drawable.ic_status_bad;
                int days = Math.abs(DateCalculator.daysBetween(now, dueDate));
                message = getString(R.string.tenant_status_days_overdue_format, days);
                break;
            }
            case NEXT_DUE: {
                labelRes = R.string.tenant_status_next_due_label;
                iconRes = R.drawable.ic_status_wait;
                int days = DateCalculator.daysBetween(now, dueDate);
                message = getString(R.string.tenant_status_days_until_format, days, DateCalculator.formatDdMmYyyy(dueDate));
                break;
            }
            case ACTIVE: {
                labelRes = R.string.tenant_status_active_label;
                iconRes = R.drawable.ic_status_good;
                int days = DateCalculator.daysBetween(now, dueDate);
                message = getString(R.string.tenant_status_days_until_format, days, DateCalculator.formatDdMmYyyy(dueDate));
                break;
            }
            default:
                renderEmptyStatus();
                return;
        }
        ivStatus.setVisibility(View.VISIBLE);
        ivStatus.setImageResource(iconRes);
        ivStatus.setImageTintList(android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), StatusPresentation.fgColorRes(tone))));
        tvStatusLabel.setText(labelRes);
        tvStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), StatusPresentation.fgColorRes(tone)));
        tvStatusMessage.setText(message);
    }

    private void renderEmptyStatus() {
        ivStatus.setVisibility(View.GONE);
        tvStatusLabel.setText("");
        tvStatusMessage.setText(R.string.tenant_status_empty);
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
