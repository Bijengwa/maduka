package com.maduka.rentmanager.ui.tenant.history;

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
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.PaymentRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.ui.common.PaymentRowAdapter;

import java.util.List;
import java.util.Locale;

/** Tenant's own full payment ledger - same query-scoped feed as TenantPaymentsFragment
 * (PaymentRepository.observePaymentsForTenant), with running totals and the fuller per-row
 * detail line (receipt reference, recorded-by name). No history is invented: an empty list
 * simply shows the polished empty state. */
public class TenantHistoryFragment extends Fragment {
    private final PaymentRepository paymentRepository = new PaymentRepository();

    private TextView tvStatTransactions, tvStatTotal, tvStatAverage, tvEmpty;
    private RecyclerView recyclerHistory;
    private PaymentRowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenant_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvStatTransactions = view.findViewById(R.id.tvStatTransactions);
        tvStatTotal = view.findViewById(R.id.tvStatTotal);
        tvStatAverage = view.findViewById(R.id.tvStatAverage);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        recyclerHistory = view.findViewById(R.id.recyclerHistory);
        adapter = new PaymentRowAdapter(true);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerHistory.setAdapter(adapter);

        String uid = new AuthRepository().currentUid();
        if (uid == null) return;

        paymentRepository.observePaymentsForTenant(uid, new PaymentRepository.PaymentsListener() {
            @Override
            public void onPayments(List<PaymentRecord> payments) {
                if (!isAdded()) return;
                adapter.submitList(payments);
                boolean empty = payments.isEmpty();
                recyclerHistory.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);

                long total = 0;
                for (PaymentRecord p : payments) total += p.getAmount();
                long average = payments.isEmpty() ? 0 : total / payments.size();

                tvStatTransactions.setText(String.valueOf(payments.size()));
                tvStatTotal.setText(String.format(Locale.US, "TSh %,d", total));
                tvStatAverage.setText(String.format(Locale.US, "TSh %,d", average));
            }

            @Override
            public void onError(String message) { }
        });
    }
}
