package com.maduka.rentmanager.ui.admin.dashboard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.ui.common.StatusPill;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.MoneyFormatter;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;

/** One row per PaymentRecord on the dashboard's payment list - date/tenant/shop/amount/paid-to
 * phone/due-status, per the dashboard redesign spec. The "status" here is the associated
 * tenant's CURRENT due-date bucket (Active/Next Due/Overdue), not a payment-approval state -
 * there is no approval workflow any more. */
public class DashboardPaymentAdapter extends RecyclerView.Adapter<DashboardPaymentAdapter.ViewHolder> {

    public static class Row {
        final PaymentRecord payment;
        final String tenantName;
        final String shopName;
        final DateCalculator.DueBucket bucket;
        final long tenantDueDate;

        public Row(PaymentRecord payment, String tenantName, String shopName, DateCalculator.DueBucket bucket,
                   long tenantDueDate) {
            this.payment = payment;
            this.tenantName = tenantName;
            this.shopName = shopName;
            this.bucket = bucket;
            this.tenantDueDate = tenantDueDate;
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
                .inflate(R.layout.item_dashboard_payment_row, parent, false);
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
        private final TextView tvDate, tvTenant, tvAmount, tvShopDate, tvDueStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvTenant = itemView.findViewById(R.id.tvTenant);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvShopDate = itemView.findViewById(R.id.tvShopDate);
            tvDueStatus = itemView.findViewById(R.id.tvDueStatus);
        }

        void bind(Row row) {
            tvDate.setText(DateCalculator.formatDdMm(row.payment.getPaymentDate()));
            tvTenant.setText(row.tenantName);
            tvAmount.setText(itemView.getContext().getString(R.string.dashboard_amount_format,
                    MoneyFormatter.compact(row.payment.getAmount())));

            String months = itemView.getContext().getString(R.string.months_covered_format, row.payment.getMonthsCovered());
            String recordedBy = row.payment.getRecordedByName();
            StringBuilder meta = new StringBuilder(row.shopName).append(" · ").append(months);
            if (recordedBy != null && !recordedBy.isEmpty()) meta.append(" · ").append(recordedBy);
            tvShopDate.setText(meta.toString());

            StatusPresentation.Tone tone = StatusPresentation.toneFor(row.bucket);
            int labelRes;
            switch (row.bucket) {
                case ACTIVE: labelRes = R.string.dashboard_filter_active; break;
                case NEXT_DUE: labelRes = R.string.dashboard_filter_next_due; break;
                case OVERDUE: labelRes = R.string.dashboard_filter_overdue; break;
                default: labelRes = R.string.label_date_not_set;
            }
            StatusPill.apply(tvDueStatus, tone, labelRes);
        }
    }
}
