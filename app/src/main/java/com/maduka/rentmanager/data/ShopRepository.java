package com.maduka.rentmanager.data;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.Shop;

import java.util.ArrayList;
import java.util.List;

public class ShopRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface ShopsListener { void onShops(List<Shop> shops); void onError(String message); }
    public interface ShopListener { void onShop(Shop shop); void onError(String message); }

    /** A single shop's own record, live-updating - used by a Tenant's own Details screen so it
     * never has to pull the full shops/ node just to find one row. */
    public void observeShop(String shopId, ShopListener listener) {
        if (shopId == null || shopId.isEmpty()) {
            listener.onError("Invalid shop id.");
            return;
        }
        fb.root().child(FirebaseSchema.SHOPS).child(shopId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onShop(snapshot.getValue(Shop.class)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** All registered shops, live-updating, sorted by shopId (a Firebase push key, which sorts
     * chronologically by creation time). */
    public void observeShops(ShopsListener listener) {
        fb.root().child(FirebaseSchema.SHOPS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                listener.onShops(parseShops(snapshot));
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Single-read variant of observeShops: fires exactly once with the current shop list, then
     * automatically detaches - no dangling listener to worry about. Used wherever a screen just
     * needs a current snapshot (AddShopActivity's uniqueness check, RegisterTenantActivity's
     * vacant-shop picker) rather than a live subscription that would otherwise leak for the
     * Activity's lifetime. */
    public void observeShopsOnce(ShopsListener listener) {
        fb.root().child(FirebaseSchema.SHOPS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                listener.onShops(parseShops(snapshot));
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    private List<Shop> parseShops(DataSnapshot snapshot) {
        List<Shop> shops = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            Shop s = child.getValue(Shop.class);
            if (s != null) shops.add(s);
        }
        shops.sort((a, b) -> a.getShopId().compareTo(b.getShopId()));
        return shops;
    }

    /** Registers a new shop under a fresh push key, with a Super-Admin-chosen name and a
     * monthly rent amount. Rejects the call (via cb.onError) if a shop with the same name
     * (case-insensitive, trimmed) already exists in the given current list - the caller
     * (AddShopActivity) supplies that list from a current observeShopsOnce read so this check
     * doesn't need a separate network round-trip. */
    public void addShop(String name, long monthlyRent, List<Shop> existingShops, FirebaseManager.Callback<Void> cb) {
        String trimmedName = name.trim();
        for (Shop existing : existingShops) {
            if (existing.getName() != null && existing.getName().trim().equalsIgnoreCase(trimmedName)) {
                Context context = FirebaseApp.getInstance().getApplicationContext();
                cb.onError(context.getString(R.string.properties_error_duplicate_name, trimmedName));
                return;
            }
        }

        DatabaseReference shopRef = fb.root().child(FirebaseSchema.SHOPS).push();
        long now = System.currentTimeMillis();
        Shop shop = new Shop();
        shop.setShopId(shopRef.getKey());
        shop.setName(trimmedName);
        shop.setMonthlyRent(monthlyRent);
        shop.setOccupied(false);
        shop.setTenantUid(null);
        shop.setCreatedAt(now);
        shop.setUpdatedAt(now);

        shopRef.setValue(shop)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
