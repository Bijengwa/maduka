package com.maduka.rentmanager.ui.tenant.payments;

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
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.PaymentRowAdapter;

import java.util.List;
import java.util.Locale;

/** Tenant's own payment records - PaymentRepository.observePaymentsForTenant(uid) is a real
 * server-side query, never a client-side filter of the full payments/ node. */
public class TenantPaymentsFragment extends Fragment {
    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();
    private final PaymentRepository paymentRepository = new PaymentRepository();

    private TextView tvSubtitle, tvEmpty;
    private RecyclerView recyclerPayments;
    private PaymentRowAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenant_payments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        recyclerPayments = view.findViewById(R.id.recyclerPayments);
        adapter = new PaymentRowAdapter(false);
        recyclerPayments.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerPayments.setAdapter(adapter);

        String uid = new AuthRepository().currentUid();
        if (uid == null) return;

        tenantRepository.observeTenant(uid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (!isAdded() || tenant == null) return;
                shopRepository.observeShop(tenant.getShopId(), new ShopRepository.ShopListener() {
                    @Override
                    public void onShop(Shop shop) {
                        if (!isAdded()) return;
                        String shopName = shop != null && shop.getName() != null ? shop.getName() : tenant.getShopId();
                        tvSubtitle.setText(getString(R.string.tenant_payments_subtitle_format, shopName,
                                String.format(Locale.US, "%,d", tenant.getMonthlyRent())));
                    }

                    @Override
                    public void onError(String message) { }
                });
            }

            @Override
            public void onError(String message) { }
        });

        paymentRepository.observePaymentsForTenant(uid, new PaymentRepository.PaymentsListener() {
            @Override
            public void onPayments(List<PaymentRecord> payments) {
                if (!isAdded()) return;
                adapter.submitList(payments);
                boolean empty = payments.isEmpty();
                recyclerPayments.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) { }
        });
    }
}
