package com.maduka.rentmanager.ui.admin.properties;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.StatusPill;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Renders a Shop joined with its occupying Tenant (if any) - the join happens once in
 * PropertiesFragment (which observes both ShopRepository and TenantRepository), not per-row,
 * so this adapter stays a pure renderer. */
public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {

    /** One row: a shop, and its current tenant if occupied. */
    public static class Row {
        public final Shop shop;
        public final Tenant tenant;
        public Row(Shop shop, Tenant tenant) { this.shop = shop; this.tenant = tenant; }
    }

    private final List<Row> rows = new ArrayList<>();

    public void submitList(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shop_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View accentBar;
        private final TextView tvShopId;
        private final TextView tvRent;
        private final TextView tvStatus;
        private final TextView tvTenantLine;
        private final View rowPaymentInfo;
        private final TextView tvLastPayment;
        private final TextView tvDueDate;
        private final TextView tvDaysRemaining;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            accentBar = itemView.findViewById(R.id.accentBar);
            tvShopId = itemView.findViewById(R.id.tvShopId);
            tvRent = itemView.findViewById(R.id.tvRent);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTenantLine = itemView.findViewById(R.id.tvTenantLine);
            rowPaymentInfo = itemView.findViewById(R.id.rowPaymentInfo);
            tvLastPayment = itemView.findViewById(R.id.tvLastPayment);
            tvDueDate = itemView.findViewById(R.id.tvDueDate);
            tvDaysRemaining = itemView.findViewById(R.id.tvDaysRemaining);
        }

        void bind(Row row) {
            if (row == null || row.shop == null) return;
            Shop shop = row.shop;
            Tenant tenant = row.tenant;
            String name = shop.getName() != null && !shop.getName().isEmpty()
                    ? shop.getName()
                    : (shop.getShopId() != null ? shop.getShopId() : "");
            tvShopId.setText(name);

            if (!shop.isOccupied() || tenant == null) {
                tvRent.setText(R.string.label_rent_not_set);
                tvTenantLine.setText(R.string.label_no_tenant);
                rowPaymentInfo.setVisibility(View.GONE);
                StatusPill.apply(tvStatus, StatusPresentation.Tone.EMPTY, R.string.status_vacant);
                StatusPill.accent(accentBar, StatusPresentation.Tone.EMPTY);
                return;
            }

            tvRent.setText(String.format(Locale.US, "TSh %,d", tenant.getMonthlyRent()));
            String phone = tenant.getPhone() != null ? tenant.getPhone() : "";
            String tenantName = tenant.getName() != null ? tenant.getName() : "";
            tvTenantLine.setText(phone.isEmpty() ? tenantName : tenantName + "  ·  " + phone);

            long now = System.currentTimeMillis();
            boolean overdue = DateCalculator.isOverdue(tenant.getDueDate(), now);
            StatusPresentation.Tone tone = overdue ? StatusPresentation.Tone.BAD : StatusPresentation.Tone.GOOD;
            StatusPill.apply(tvStatus, tone, overdue ? R.string.status_overdue : R.string.status_occupied);
            StatusPill.accent(accentBar, tone);

            rowPaymentInfo.setVisibility(View.VISIBLE);
            tvLastPayment.setText(DateCalculator.formatDdMmYyyy(tenant.getLastPaymentDate()));
            tvDueDate.setText(DateCalculator.formatDdMmYyyy(tenant.getDueDate()));
            int days = DateCalculator.daysBetween(now, tenant.getDueDate());
            tvDaysRemaining.setText(overdue
                    ? itemView.getContext().getString(R.string.label_days_overdue_format, Math.abs(days))
                    : itemView.getContext().getString(R.string.label_days_left_format, days));
        }
    }
}
