package com.maduka.rentmanager.ui.admin.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.List;

/** Rows for the dashboard's "Malipo Yanayokaribia / Upcoming Payments" section - active tenants
 * whose dueDate falls within the next 30 calendar days, per the dashboard redesign spec. */
public class UpcomingPaymentAdapter extends RecyclerView.Adapter<UpcomingPaymentAdapter.ViewHolder> {

    public static class Row {
        final Tenant tenant;
        final String shopName;

        public Row(Tenant tenant, String shopName) {
            this.tenant = tenant;
            this.shopName = shopName;
        }
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
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_upcoming_payment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(rows.get(position));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName, tvDue, tvAmount;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvDue = itemView.findViewById(R.id.tvDue);
            tvAmount = itemView.findViewById(R.id.tvAmount);
        }

        void bind(Row row) {
            tvName.setText(row.tenant.getName() + " · " + row.shopName);
            long now = System.currentTimeMillis();
            int days = DateCalculator.daysBetween(now, row.tenant.getDueDate());
            String dueDate = itemView.getContext().getString(R.string.dashboard_upcoming_due_date_format,
                    DateCalculator.formatDdMmYyyy(row.tenant.getDueDate()));
            String daysLeft = itemView.getContext().getString(R.string.label_days_left_format, Math.max(0, days));
            tvDue.setText(dueDate + " · " + daysLeft);
            tvAmount.setText(itemView.getContext().getString(R.string.dashboard_amount_format,
                    MoneyFormatter.compact(row.tenant.getMonthlyRent())));
        }
    }
}
