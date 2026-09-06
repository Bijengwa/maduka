package com.maduka.rentmanager.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.PaymentStatus;
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
    private final FirebaseManager fb = FirebaseManager.get();

    public interface TenantsListener { void onTenants(List<Tenant> tenants); void onError(String message); }
    public interface TenantListener { void onTenant(Tenant tenant); void onError(String message); }

    /** All registered tenants, live-updating. Admin/Super Admin only - a Tenant's own screens
     * must use observeTenant(uid, ...) below instead, never filter this full list client-side. */
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

    /** A single tenant's own record, live-updating - server-side scoped by uid, not a filter
     * over observeTenants(). This is what every Tenant-self screen must use. */
    public void observeTenant(String uid, TenantListener listener) {
        if (uid == null || uid.isEmpty()) {
            listener.onError("Invalid tenant uid.");
            return;
        }
        fb.root().child(FirebaseSchema.TENANTS).child(uid).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onTenant(snapshot.getValue(Tenant.class)); }
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

    /** Registers a tenant into a specific (must be currently vacant) shop, recording their
     * first rent payment at the same time (months paid for, starting from paymentDateMillis -
     * the due date is derived via DateCalculator.addMonths from that start date, calendar-month
     * accurate, not a fixed 30-day block). Uses the same secondary-FirebaseApp pattern as
     * UserRepository.registerAdmin, so the currently signed-in Admin/Super Admin's own session
     * is never disturbed by creating this new Firebase Auth account. Marks the shop occupied.
     *
     * The initial payment is recorded already CONFIRMED, not PENDING: the ongoing two-person
     * confirm/reject rule (a second admin must confirm a payment someone else recorded) exists
     * for later rent renewals, once there's a dashboard to actually review pending payments -
     * that dashboard doesn't exist yet in this app, so leaving this first payment PENDING would
     * create a record nobody could ever act on. recordedByUid is stamped as both recorder and
     * confirmer for this one record, since registering the tenant is itself the confirming act. */
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
                    payment.setStatus(PaymentStatus.CONFIRMED);
                    payment.setConfirmedByUid(recordedByUid);
                    payment.setPreviousLastPaymentDate(0L);
                    payment.setPreviousDueDate(0L);
                    payment.setPreviousMonthsCovered(0);
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
