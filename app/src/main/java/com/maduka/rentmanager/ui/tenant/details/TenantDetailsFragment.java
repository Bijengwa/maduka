package com.maduka.rentmanager.ui.tenant.details;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.ShopRepository;
import com.maduka.rentmanager.data.TenantRepository;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.ui.common.StatusPill;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.Locale;

/** Tenant's own shop/lease details - retrieves only the signed-in tenant's own record
 * (observeTenant(uid)) and their own shop (observeShop(shopId)), never the full portfolio. */
public class TenantDetailsFragment extends Fragment {
    private final TenantRepository tenantRepository = new TenantRepository();
    private final ShopRepository shopRepository = new ShopRepository();

    private TextView tvSubtitle;
    private Row rowShop, rowPhone, rowEmail, rowRent, rowMoveIn, rowDueDate, rowStatus;

    private static class Row {
        final TextView label, value;
        Row(View root) {
            label = root.findViewById(R.id.tvLabel);
            value = root.findViewById(R.id.tvValue);
        }
        void set(String labelText, String valueText) {
            label.setText(labelText);
            value.setText(valueText);
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tenant_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tvSubtitle = view.findViewById(R.id.tvSubtitle);
        rowShop = new Row(view.findViewById(R.id.rowShop));
        rowPhone = new Row(view.findViewById(R.id.rowPhone));
        rowEmail = new Row(view.findViewById(R.id.rowEmail));
        rowRent = new Row(view.findViewById(R.id.rowRent));
        rowMoveIn = new Row(view.findViewById(R.id.rowMoveIn));
        rowDueDate = new Row(view.findViewById(R.id.rowDueDate));
        rowStatus = new Row(view.findViewById(R.id.rowStatus));

        String uid = new AuthRepository().currentUid();
        if (uid == null) return;
        tenantRepository.observeTenant(uid, new TenantRepository.TenantListener() {
            @Override
            public void onTenant(Tenant tenant) {
                if (!isAdded() || tenant == null) return;
                bind(tenant);
            }

            @Override
            public void onError(String message) { }
        });
    }

    private void bind(Tenant tenant) {
        shopRepository.observeShop(tenant.getShopId(), new ShopRepository.ShopListener() {
            @Override
            public void onShop(Shop shop) {
                if (!isAdded()) return;
                String shopName = shop != null && shop.getName() != null ? shop.getName() : tenant.getShopId();
                tvSubtitle.setText(shopName);
                rowShop.set(getString(R.string.tenant_details_shop_label), shopName);
            }

            @Override
            public void onError(String message) { }
        });

        rowPhone.set(getString(R.string.tenant_details_phone), tenant.getPhone() != null ? tenant.getPhone() : "—");
        rowEmail.set(getString(R.string.tenant_details_email), tenant.getEmail() != null ? tenant.getEmail() : "—");
        rowRent.set(getString(R.string.tenant_details_monthly_rent),
                String.format(Locale.US, "TSh %,d", tenant.getMonthlyRent()));
        rowMoveIn.set(getString(R.string.tenant_details_move_in_date), DateCalculator.formatDdMmYyyy(tenant.getMoveInDate()));
        rowDueDate.set(getString(R.string.label_due_date), DateCalculator.formatDdMmYyyy(tenant.getDueDate()));

        PresenceStatus presence = tenant.getPresenceStatus() != null ? tenant.getPresenceStatus() : PresenceStatus.YUPO;
        boolean overdue = DateCalculator.isOverdue(tenant.getDueDate(), System.currentTimeMillis());
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
        StatusPill.apply(rowStatus.value, tone, labelRes);
        rowStatus.label.setText(getString(R.string.tenant_details_status));
    }
}
