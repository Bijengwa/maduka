package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.Shop;

import java.util.ArrayList;
import java.util.List;

public class ShopRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface ShopsListener { void onShops(List<Shop> shops); void onError(String message); }

    /** All registered shops, live-updating, sorted by shopId (A1, A2, ... A10). */
    public void observeShops(ShopsListener listener) {
        fb.root().child(FirebaseSchema.SHOPS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Shop> shops = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Shop s = child.getValue(Shop.class);
                    if (s != null) shops.add(s);
                }
                shops.sort((a, b) -> a.getShopId().compareTo(b.getShopId()));
                listener.onShops(shops);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Registers a new shop with a monthly rent amount. shopId must be one of A1..A10 and not
     * already registered - check against the current in-memory list in the Fragment before
     * calling this, this method itself does not re-check (Firebase security rules are the real
     * enforcement layer, added in a later task). */
    public void addShop(String shopId, long monthlyRent, FirebaseManager.Callback<Void> cb) {
        long now = System.currentTimeMillis();
        Shop shop = new Shop();
        shop.setShopId(shopId);
        shop.setMonthlyRent(monthlyRent);
        shop.setOccupied(false);
        shop.setTenantUid(null);
        shop.setCreatedAt(now);
        shop.setUpdatedAt(now);

        fb.root().child(FirebaseSchema.SHOPS).child(shopId).setValue(shop)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
