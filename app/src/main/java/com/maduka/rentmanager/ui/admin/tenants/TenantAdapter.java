package com.maduka.rentmanager.ui.admin.tenants;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.StatusPill;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TenantAdapter extends RecyclerView.Adapter<TenantAdapter.ViewHolder> {

    public interface OnEndTenancyListener { void onEndTenancy(Tenant tenant); }

    private final List<Tenant> tenants = new ArrayList<>();
    private Map<String, String> shopNamesById = new HashMap<>();
    private boolean showEndTenancyAction;
    private OnEndTenancyListener endTenancyListener;

    public void submitList(List<Tenant> newTenants) {
        tenants.clear();
        tenants.addAll(newTenants);
        notifyDataSetChanged();
    }

    /** Ending a tenancy is Super-Admin-only - TenantsFragment passes true only for that role. */
    public void setShowEndTenancyAction(boolean show) {
        this.showEndTenancyAction = show;
        notifyDataSetChanged();
    }

    public void setOnEndTenancyListener(OnEndTenancyListener listener) {
        this.endTenancyListener = listener;
    }

    public String shopNameFor(String shopId) {
        String name = shopNamesById.get(shopId);
        return name != null ? name : shopId;
    }

    /** Maps shopId -> display name, so the card can show the tenant's shop by name rather than
     * its internal Firebase key. Supplied separately from TenantsFragment's ShopRepository feed
     * since Tenant itself only stores shopId. */
    public void setShopNames(Map<String, String> newShopNames) {
        shopNamesById = newShopNames;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_tenant_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(tenants.get(position), shopNamesById, showEndTenancyAction, endTenancyListener);
    }

    @Override
    public int getItemCount() { return tenants.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View accentBar;
        private final TextView tvName;
        private final TextView tvContact;
        private final TextView tvStatus;
        private final TextView tvRent;
        private final TextView tvLastPayment;
        private final TextView tvDueDate;
        private final TextView tvDaysRemaining;
        private final TextView tvEndTenancy;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            accentBar = itemView.findViewById(R.id.accentBar);
            tvName = itemView.findViewById(R.id.tvName);
            tvContact = itemView.findViewById(R.id.tvContact);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvRent = itemView.findViewById(R.id.tvRent);
            tvLastPayment = itemView.findViewById(R.id.tvLastPayment);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvDaysRemaining = itemView.findViewById(R.id.tvDaysRemaining);
            tvEndTenancy = itemView.findViewById(R.id.tvEndTenancy);
        }

        void bind(Tenant tenant, Map<String, String> shopNamesById, boolean showEndTenancyAction,
                  OnEndTenancyListener endTenancyListener) {
            tvName.setText(tenant.getName());

            String phone = tenant.getPhone() != null ? tenant.getPhone() : "";
            String shopName = shopNamesById.get(tenant.getShopId());
            if (shopName == null) shopName = tenant.getShopId();

            StringBuilder detail = new StringBuilder();
            if (!phone.isEmpty()) detail.append(phone);
            if (detail.length() > 0) detail.append("  ·  ");
            detail.append(shopName);
            tvContact.setText(detail.toString());

            tvRent.setText(String.format(Locale.US, "TSh %,d /month", tenant.getMonthlyRent()));

            long now = System.currentTimeMillis();
            boolean overdue = DateCalculator.isOverdue(tenant.getDueDate(), now);
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
            StatusPill.accent(accentBar, tone);

            String notSet = itemView.getContext().getString(R.string.label_date_not_set);
            tvLastPayment.setText(DateCalculator.formatDueDateOrUnknown(tenant.getLastPaymentDate(), notSet));
            tvDueDate.setText(DateCalculator.formatDueDateOrUnknown(tenant.getDueDate(), notSet));
            if (!DateCalculator.hasValidDueDate(tenant.getDueDate())) {
                tvDaysRemaining.setText(notSet);
            } else {
                int days = DateCalculator.daysBetween(now, tenant.getDueDate());
                tvDaysRemaining.setText(overdue
                        ? itemView.getContext().getString(R.string.label_days_overdue_format, Math.abs(days))
                        : itemView.getContext().getString(R.string.label_days_left_format, days));
            }

            tvEndTenancy.setVisibility(showEndTenancyAction ? View.VISIBLE : View.GONE);
            tvEndTenancy.setOnClickListener(v -> {
                if (endTenancyListener != null) endTenancyListener.onEndTenancy(tenant);
            });
        }
    }
}
