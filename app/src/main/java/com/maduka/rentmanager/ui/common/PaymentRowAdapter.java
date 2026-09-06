package com.maduka.rentmanager.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.PaymentStatus;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared payment-row renderer used by both TenantPaymentsFragment and TenantHistoryFragment -
 * same underlying PaymentRepository.observePaymentsForTenant feed, two different levels of
 * detail in the subtitle line. */
public class PaymentRowAdapter extends RecyclerView.Adapter<PaymentRowAdapter.ViewHolder> {

    /** true shows the fuller "History" subtitle (method, receipt ref, recorded by); false shows
     * the shorter "Payments" subtitle (recorded by, months, method). */
    private final boolean detailed;
    private final List<PaymentRecord> payments = new ArrayList<>();

    public PaymentRowAdapter(boolean detailed) { this.detailed = detailed; }

    public void submitList(List<PaymentRecord> newPayments) {
        payments.clear();
        payments.addAll(newPayments);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_payment_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(payments.get(position), detailed);
    }

    @Override
    public int getItemCount() { return payments.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvDate, tvDetail, tvAmount, tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvDetail = itemView.findViewById(R.id.tvDetail);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(PaymentRecord payment, boolean detailed) {
            tvDate.setText(DateCalculator.formatDdMmYyyy(payment.getPaymentDate()));
            String recordedBy = payment.getRecordedByName() != null ? payment.getRecordedByName() : "";
            String months = itemView.getContext().getString(R.string.months_covered_format, payment.getMonthsCovered());
            String method = payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "";

            StringBuilder detail = new StringBuilder();
            detail.append(months);
            if (!method.isEmpty()) detail.append(" · ").append(method);
            if (detailed) {
                if (payment.getReceiptReference() != null && !payment.getReceiptReference().isEmpty()) {
                    detail.append(" · ").append(payment.getReceiptReference());
                }
                if (!recordedBy.isEmpty()) {
                    detail.append(" · ").append(itemView.getContext().getString(R.string.recorded_by_format, recordedBy));
                }
            } else if (!recordedBy.isEmpty()) {
                detail.insert(0, recordedBy + " · ");
            }
            tvDetail.setText(detail.toString());

            tvAmount.setText(String.format(Locale.US, "TSh %,d", payment.getAmount()));

            PaymentStatus status = payment.getStatus() != null ? payment.getStatus() : PaymentStatus.PENDING;
            StatusPresentation.Tone tone = StatusPresentation.toneFor(status);
            int labelRes;
            switch (status) {
                case CONFIRMED: labelRes = R.string.status_confirmed; break;
                case REJECTED: labelRes = R.string.status_rejected; break;
                default: labelRes = R.string.status_pending;
            }
            StatusPill.apply(tvStatus, tone, labelRes);
        }
    }
}
