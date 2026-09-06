package com.maduka.rentmanager.ui.admin.tenants;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TenantAdapter extends RecyclerView.Adapter<TenantAdapter.ViewHolder> {
    private final List<Tenant> tenants = new ArrayList<>();
    private Map<String, String> shopNamesById = new HashMap<>();

    public void submitList(List<Tenant> newTenants) {
        tenants.clear();
        tenants.addAll(newTenants);
        notifyDataSetChanged();
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
        holder.bind(tenants.get(position), shopNamesById);
    }

    @Override
    public int getItemCount() { return tenants.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View accentBar;
        private final TextView tvName;
        private final TextView tvContact;
        private final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            accentBar = itemView.findViewById(R.id.accentBar);
            tvName = itemView.findViewById(R.id.tvName);
            tvContact = itemView.findViewById(R.id.tvContact);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(Tenant tenant, Map<String, String> shopNamesById) {
            tvName.setText(tenant.getName());

            String phone = tenant.getPhone() != null ? tenant.getPhone() : "";
            String email = tenant.getEmail() != null ? tenant.getEmail() : "";
            String shopName = shopNamesById.get(tenant.getShopId());
            if (shopName == null) shopName = tenant.getShopId();

            StringBuilder detail = new StringBuilder();
            if (!phone.isEmpty()) detail.append(phone);
            if (!email.isEmpty()) {
                if (detail.length() > 0) detail.append("  ·  ");
                detail.append(email);
            }
            if (detail.length() > 0) detail.append("  ·  ");
            detail.append(shopName);
            tvContact.setText(detail.toString());

            PresenceStatus status = tenant.getPresenceStatus() != null ? tenant.getPresenceStatus() : PresenceStatus.YUPO;
            StatusPresentation.Tone tone = StatusPresentation.toneFor(status);
            tvStatus.setText(status == PresenceStatus.YUPO ? R.string.status_active : R.string.status_disabled);
            applyPill(tvStatus, tone);
            applyAccent(accentBar, tone);
        }

        private void applyPill(TextView view, StatusPresentation.Tone tone) {
            Context context = view.getContext();
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(context.getResources().getDimension(R.dimen.radius_full));
            bg.setColor(ContextCompat.getColor(context, StatusPresentation.bgColorRes(tone)));
            int strokeWidth = Math.round(context.getResources().getDisplayMetrics().density);
            bg.setStroke(strokeWidth, ContextCompat.getColor(context, StatusPresentation.borderColorRes(tone)));
            view.setBackground(bg);
            view.setTextColor(ContextCompat.getColor(context, StatusPresentation.fgColorRes(tone)));
        }

        private void applyAccent(View view, StatusPresentation.Tone tone) {
            view.setBackgroundColor(ContextCompat.getColor(view.getContext(), StatusPresentation.borderColorRes(tone)));
        }
    }
}
