package com.maduka.rentmanager.ui.admin.properties;

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
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.util.StatusPresentation;

import java.util.ArrayList;
import java.util.List;

public class ShopAdapter extends RecyclerView.Adapter<ShopAdapter.ViewHolder> {
    private final List<Shop> shops = new ArrayList<>();

    public void submitList(List<Shop> newShops) {
        shops.clear();
        shops.addAll(newShops);
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
        holder.bind(shops.get(position));
    }

    @Override
    public int getItemCount() { return shops.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvShopId;
        private final TextView tvRent;
        private final TextView tvStatus;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvShopId = itemView.findViewById(R.id.tvShopId);
            tvRent = itemView.findViewById(R.id.tvRent);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }

        void bind(Shop shop) {
            tvShopId.setText(shop.getShopId());
            tvRent.setText("TSh " + shop.getMonthlyRent());

            boolean occupied = shop.isOccupied();
            StatusPresentation.Tone tone = occupied ? StatusPresentation.Tone.GOOD : StatusPresentation.Tone.EMPTY;
            tvStatus.setText(occupied ? R.string.status_occupied : R.string.status_vacant);
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
