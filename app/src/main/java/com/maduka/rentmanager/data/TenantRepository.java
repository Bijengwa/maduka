package com.maduka.rentmanager.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.Shop;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.DateCalculator;
import com.maduka.rentmanager.util.LoginKeyUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TenantRepository {
    private static final String TAG = "TenantRepository";
    private final FirebaseManager fb = FirebaseManager.get();

    public interface TenantsListener { void onTenants(List<Tenant> tenants); void onError(String message); }
    public interface TenantListener { void onTenant(Tenant tenant); void onError(String message); }

    /** All registered tenants, live-updating. Admin/Super Admin only - a Tenant's own screens
     * must use observeTenant(uid, ...) below instead, never filter this full list client-side. */
    public ValueEventListener observeTenants(TenantsListener listener) {
        ValueEventListener registration = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                listener.onTenants(parseTenants(snapshot));
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        };
        fb.root().child(FirebaseSchema.TENANTS).addValueEventListener(registration);
        return registration;
    }

    public void stopObservingTenants(ValueEventListener registration) {
        if (registration != null) {
            fb.root().child(FirebaseSchema.TENANTS).removeEventListener(registration);
        }
    }

    private List<Tenant> parseTenants(DataSnapshot snapshot) {
        List<Tenant> tenants = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            Tenant t = readTenant(child);
            if (t != null) tenants.add(t);
        }
        return tenants;
    }

    private Tenant readTenant(DataSnapshot child) {
        Tenant t;
        try {
            t = child.getValue(Tenant.class);
        } catch (DatabaseException e) {
            Log.w(TAG, "Skipping malformed tenant " + child.getKey(), e);
            return null;
        }
        if (t == null) return null;
        if (t.getUid() == null || t.getUid().isEmpty()) {
            t.setUid(child.getKey());
        }
        return t;
    }

    /** A single tenant's own record, live-updating - server-side scoped by uid, not a filter
     * over observeTenants(). This is what every Tenant-self screen must use. Returns the
     * registration so callers can detach it in onDestroyView via stopObservingTenant. */
    public ValueEventListener observeTenant(String uid, TenantListener listener) {
        if (uid == null || uid.isEmpty()) {
            listener.onError("Invalid tenant uid.");
            return null;
        }
        ValueEventListener registration = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onTenant(readTenant(snapshot)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        };
        fb.root().child(FirebaseSchema.TENANTS).child(uid).addValueEventListener(registration);
        return registration;
    }

    public void stopObservingTenant(String uid, ValueEventListener registration) {
        if (registration != null && uid != null) {
            fb.root().child(FirebaseSchema.TENANTS).child(uid).removeEventListener(registration);
        }
    }

    /** One-shot read of a single tenant by uid - for background/receiver code (e.g.
     * OverdueCheckReceiver) that must not leave a live listener running after it returns. */
    public void observeTenantOnce(String uid, TenantListener listener) {
        if (uid == null || uid.isEmpty()) {
            listener.onError("Invalid tenant uid.");
            return;
        }
        fb.root().child(FirebaseSchema.TENANTS).child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onTenant(readTenant(snapshot)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** One-shot read of all tenants - for background/receiver code only (Admin/Super Admin
     * overdue summary). UI screens must keep using the live observeTenants() above. */
    public void observeTenantsOnce(TenantsListener listener) {
        fb.root().child(FirebaseSchema.TENANTS).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onTenants(parseTenants(snapshot)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }

    /** Yupo/Hayupo presence-check button handler: records whether an overdue tenant is still
     * occupying their shop. Hayupo does not by itself vacate the shop - that stays a distinct,
     * explicit Super Admin action - it only flags the tenant for management follow-up. */
    public void setPresence(String tenantUid, PresenceStatus status, long nowMillis, FirebaseManager.Callback<Void> cb) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("presenceStatus", status.name());
        updates.put("lastPresenceCheckAt", nowMillis);
        updates.put("updatedAt", nowMillis);
        fb.root().child(FirebaseSchema.TENANTS).child(tenantUid).updateChildren(updates)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** The distinct, explicit "tenant left" action setPresence() above deliberately does not
     * perform: frees the shop (occupied=false, tenantUid cleared) and unassigns the tenant
     * (shopId cleared) in one atomic multi-path write. The Tenant node and every historical
     * PaymentRecord are left untouched - an unassigned shopId is what makes a tenant "former"
     * (excluded from the active tenant/shop join everywhere else already reads), the simplest
     * model that satisfies this without a separate archived/former flag or table. */
    public void endTenancy(String tenantUid, String shopId, long nowMillis, FirebaseManager.Callback<Void> cb) {
        Map<String, Object> writes = new HashMap<>();
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenantUid + "/shopId", null);
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenantUid + "/updatedAt", nowMillis);
        writes.put("/" + FirebaseSchema.SHOPS + "/" + shopId + "/occupied", false);
        writes.put("/" + FirebaseSchema.SHOPS + "/" + shopId + "/tenantUid", null);
        writes.put("/" + FirebaseSchema.SHOPS + "/" + shopId + "/updatedAt", nowMillis);
        fb.root().updateChildren(writes)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    /** Registers a tenant into a specific (must be currently vacant) shop, recording their
     * first rent payment at the same time (months paid for, starting from paymentDateMillis -
     * the due date is derived via DateCalculator.addMonths from that start date, calendar-month
     * accurate, not a fixed 30-day block). Uses the same secondary-FirebaseApp pattern as
     * UserRepository.registerAdmin, so the currently signed-in Admin/Super Admin's own session
     * is never disturbed by creating this new Firebase Auth account. Marks the shop occupied.
     *
     * There is no approval workflow: this first payment, like every payment recorded via
     * {@link PaymentRepository#recordPayment}, is immediately a valid recorded payment. */
    public void registerTenant(String name, String phone, String email, String password, Shop shop,
                                int monthsPaid, long paymentDateMillis, String recordedByUid,
                                FirebaseManager.Callback<Void> cb) {
        FirebaseAuth secondaryAuth = SecondaryAuthProvider.get();
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    secondaryAuth.signOut();

                    long now = System.currentTimeMillis();
                    long dueDate = DateCalculator.addMonths(paymentDateMillis, monthsPaid);
                    long amount = monthsPaid * shop.getMonthlyRent();

                    Tenant tenant = new Tenant();
                    tenant.setUid(uid);
                    tenant.setName(name);
                    tenant.setPhone(phone);
                    tenant.setEmail(email);
                    tenant.setAuthEmail(email);
                    tenant.setShopId(shop.getShopId());
                    tenant.setMonthlyRent(shop.getMonthlyRent());
                    tenant.setMoveInDate(paymentDateMillis);
                    tenant.setLastPaymentDate(paymentDateMillis);
                    tenant.setDueDate(dueDate);
                    tenant.setMonthsCovered(monthsPaid);
                    tenant.setPresenceStatus(PresenceStatus.YUPO);
                    tenant.setLastPresenceCheckAt(now);
                    tenant.setCreatedAt(now);
                    tenant.setUpdatedAt(now);

                    DatabaseReference paymentRef = fb.root().child(FirebaseSchema.PAYMENTS).push();
                    PaymentRecord payment = new PaymentRecord();
                    payment.setPaymentId(paymentRef.getKey());
                    payment.setTenantUid(uid);
                    payment.setShopId(shop.getShopId());
                    payment.setAmount(amount);
                    payment.setMonthsCovered(monthsPaid);
                    payment.setPaymentDate(paymentDateMillis);
                    payment.setRecordedByUid(recordedByUid);
                    payment.setCreatedAt(now);
                    payment.setUpdatedAt(now);

                    String loginKey = LoginKeyUtil.sanitize(email);

                    Map<String, Object> writes = new HashMap<>();
                    writes.put("/" + FirebaseSchema.TENANTS + "/" + uid, tenant);
                    writes.put("/" + FirebaseSchema.LOGIN_INDEX + "/" + UserRole.TENANT.node + "/" + loginKey, email);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/occupied", true);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/tenantUid", uid);
                    writes.put("/" + FirebaseSchema.SHOPS + "/" + shop.getShopId() + "/updatedAt", now);
                    writes.put("/" + FirebaseSchema.PAYMENTS + "/" + paymentRef.getKey(), payment);

                    fb.root().updateChildren(writes)
                            .addOnSuccessListener(v -> cb.onSuccess(null))
                            .addOnFailureListener(e -> cb.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
