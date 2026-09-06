package com.maduka.rentmanager.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maduka.rentmanager.R;

import java.util.ArrayList;
import java.util.List;

/** Shared row renderer for computed notification items - used by both Admin and Tenant
 * Notifications screens so the two look and behave identically. */
public class NotifAdapter extends RecyclerView.Adapter<NotifAdapter.ViewHolder> {
    private final List<NotifItem> items = new ArrayList<>();

    public void submitList(List<NotifItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View accentBar;
        private final TextView tvTitle, tvMessage, tvTime;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            accentBar = itemView.findViewById(R.id.accentBar);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            tvTime = itemView.findViewById(R.id.tvTime);
        }

        void bind(NotifItem item) {
            tvTitle.setText(item.title);
            tvMessage.setText(item.message);
            tvTime.setText(item.timeLabel);
            StatusPill.accent(accentBar, item.tone);
        }
    }
}
