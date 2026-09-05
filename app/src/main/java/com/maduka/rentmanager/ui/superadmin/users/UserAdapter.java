package com.maduka.rentmanager.ui.superadmin.users;

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
import com.maduka.rentmanager.data.model.AdminUser;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.ViewHolder> {
    private final List<AdminUser> admins = new ArrayList<>();

    public void submitList(List<AdminUser> newAdmins) {
        admins.clear();
        admins.addAll(newAdmins);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_admin_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(admins.get(position));
    }

    @Override
    public int getItemCount() { return admins.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvName;
        private final TextView tvContact;
        private final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvContact = itemView.findViewById(R.id.tvContact);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(AdminUser admin) {
            tvName.setText(admin.getName());
            String phone = admin.getPhone() != null ? admin.getPhone() : "";
            String email = admin.getEmail() != null ? admin.getEmail() : "";
            tvContact.setText(phone.isEmpty() ? email : phone + "  ·  " + email);

            PresenceStatus status = admin.getStatus() != null ? admin.getStatus() : PresenceStatus.YUPO;
            StatusPresentation.Tone tone = StatusPresentation.toneFor(status);
            tvStatus.setText(status == PresenceStatus.YUPO ? R.string.status_active : R.string.status_disabled);
            applyPill(tvStatus, tone);
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
    }
}
