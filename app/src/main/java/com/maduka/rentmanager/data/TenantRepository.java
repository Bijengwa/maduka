package com.maduka.rentmanager.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.LoginKeyUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TenantRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface TenantsListener { void onTenants(List<Tenant> tenants); void onError(String message); }

    /** All registered tenants, live-updating. */
    public void observeTenants(TenantsListener listener) {
        fb.root().child(FirebaseSchema.TENANTS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Tenant> tenants = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Tenant t = child.getValue(Tenant.class);
                    if (t != null) tenants.add(t);
                }
                listener.onTenants(tenants);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Registers a tenant into a specific (must be currently vacant) shop. Uses the same
     * secondary-FirebaseApp pattern as UserRepository.registerAdmin, so the currently signed-in
     * Admin/Super Admin's own session is never disturbed by creating this new Firebase Auth
     * account. Marks the shop occupied and stamps the tenant's monthlyRent from the shop's
     * current rent. */
    public void registerTenant(String name, String phone, String email, String password, Shop shop,
                                FirebaseManager.Callback<Void> cb) {
        FirebaseAuth secondaryAuth = SecondaryAuthProvider.get();
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    secondaryAuth.signOut();

                    long now = System.currentTimeMillis();
                    Tenant tenant = new Tenant();
                    tenant.setUid(uid);
                    tenant.setName(name);
                    tenant.setPhone(phone);
                    tenant.setEmail(email);
                    tenant.setAuthEmail(email);
                    tenant.setShopId(shop.getShopId());
                    tenant.setMonthlyRent(shop.getMonthlyRent());
                    tenant.setMoveInDate(now);
                    tenant.setPresenceStatus(PresenceStatus.YUPO);
                    tenant.setLastPresenceCheckAt(now);
                    tenant.setCreatedAt(now);
                    tenant.setUpdatedAt(now);

                    String loginKey = LoginKeyUtil.sanitize(email);

                    Map<String, Object> writes = new HashMap<>();
                    writes.put("/" + FirebaseSchema.TENANTS + "/" + uid, tenant);
                    writes.put("/" + FirebaseSchema.LOGIN_INDEX + "/" + UserRole.TENANT.node + "/" + loginKey, email);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/occupied", true);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/tenantUid", uid);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/updatedAt", now);

                    fb.root().updateChildren(writes)
                            .addOnSuccessListener(v -> cb.onSuccess(null))
                            .addOnFailureListener(e -> cb.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
