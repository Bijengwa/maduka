package com.maduka.rentmanager.data;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;
import com.maduka.rentmanager.data.model.Tenant;
import com.maduka.rentmanager.util.DateCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The payments/ node is written to by TenantRepository.registerTenant (a tenant's first
 * payment, at registration) and by recordPayment() below (every later rent renewal) -
 * Super-Admin-only per the Firebase rules, enforced there, not just in the UI. There is no
 * approval workflow: a payment recorded here is immediately valid. */
public class PaymentRepository {
    private static final String TAG = "PaymentRepository";
    private final FirebaseManager fb = FirebaseManager.get();

    public interface PaymentsListener { void onPayments(List<PaymentRecord> payments); void onError(String message); }

    /** All payment records, live-updating - Admin/Super Admin only (Reports screen). Both roles
     * are already authorized to see the full tenant/shop portfolio, so this mirrors that. */
    public ValueEventListener observePayments(PaymentsListener listener) {
        ValueEventListener registration = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                listener.onPayments(parsePayments(snapshot));
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        };
        fb.root().child(FirebaseSchema.PAYMENTS).addValueEventListener(registration);
        return registration;
    }

    public void stopObservingPayments(ValueEventListener registration) {
        if (registration != null) {
            fb.root().child(FirebaseSchema.PAYMENTS).removeEventListener(registration);
        }
    }

    /** A tenant's own payment records, live-updating - a real server-side query
     * (orderByChild("tenantUid").equalTo(uid)), not a client-side filter of the full node.
     * Returns the registration so callers can detach it in onDestroyView via
     * stopObservingPaymentsForTenant(tenantUid, registration) - the same tenantUid reconstructs
     * an equal Query, which is what removeEventListener needs to match. */
    public ValueEventListener observePaymentsForTenant(String tenantUid, PaymentsListener listener) {
        ValueEventListener registration = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PaymentRecord> payments = parsePayments(snapshot);
                payments.sort((a, b) -> Long.compare(b.getPaymentDate(), a.getPaymentDate()));
                listener.onPayments(payments);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        };
        paymentsForTenantQuery(tenantUid).addValueEventListener(registration);
        return registration;
    }

    public void stopObservingPaymentsForTenant(String tenantUid, ValueEventListener registration) {
        if (registration != null && tenantUid != null) {
            paymentsForTenantQuery(tenantUid).removeEventListener(registration);
        }
    }

    private Query paymentsForTenantQuery(String tenantUid) {
        return fb.root().child(FirebaseSchema.PAYMENTS).orderByChild("tenantUid").equalTo(tenantUid);
    }

    private List<PaymentRecord> parsePayments(DataSnapshot snapshot) {
        List<PaymentRecord> payments = new ArrayList<>();
        for (DataSnapshot child : snapshot.getChildren()) {
            PaymentRecord p = readPayment(child);
            if (p != null) payments.add(p);
        }
        return payments;
    }

    /** Malformed individual payment records (bad enum values, wrong field types) are skipped
     * with a warning log rather than crashing the whole list, matching ShopRepository/
     * TenantRepository. */
    /** Records a rent renewal payment for an already-active tenant and rolls their dueDate
     * forward by monthsPaid. Extends from the tenant's current dueDate (not from paymentDate)
     * when that dueDate is valid, so paying early doesn't shorten the next cycle; falls back to
     * paymentDate only when the tenant has no usable dueDate on record. Super-Admin-only, enforced
     * by the Firebase rules (payments/tenants .write), not just by which screens show the button. */
    public void recordPayment(Tenant tenant, int monthsPaid, long paymentDateMillis, long amount,
                               String paymentMethod, String receiptReference, String notes,
                               String paymentPhoneNumber, String recordedByUid, String recordedByName,
                               FirebaseManager.Callback<Void> cb) {
        long now = System.currentTimeMillis();
        long baseDate = DateCalculator.hasValidDueDate(tenant.getDueDate()) ? tenant.getDueDate() : paymentDateMillis;
        long newDueDate = DateCalculator.addMonths(baseDate, monthsPaid);

        DatabaseReference paymentRef = fb.root().child(FirebaseSchema.PAYMENTS).push();
        PaymentRecord payment = new PaymentRecord();
        payment.setPaymentId(paymentRef.getKey());
        payment.setTenantUid(tenant.getUid());
        payment.setShopId(tenant.getShopId());
        payment.setAmount(amount);
        payment.setMonthsCovered(monthsPaid);
        payment.setPaymentDate(paymentDateMillis);
        payment.setPaymentMethod(paymentMethod);
        payment.setReceiptReference(receiptReference);
        payment.setNotes(notes);
        payment.setPaymentPhoneNumber(paymentPhoneNumber);
        payment.setRecordedByUid(recordedByUid);
        payment.setRecordedByName(recordedByName);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        Map<String, Object> writes = new HashMap<>();
        writes.put("/" + FirebaseSchema.PAYMENTS + "/" + paymentRef.getKey(), payment);
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenant.getUid() + "/lastPaymentDate", paymentDateMillis);
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenant.getUid() + "/dueDate", newDueDate);
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenant.getUid() + "/monthsCovered", monthsPaid);
        writes.put("/" + FirebaseSchema.TENANTS + "/" + tenant.getUid() + "/updatedAt", now);

        fb.root().updateChildren(writes)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    private PaymentRecord readPayment(DataSnapshot child) {
        PaymentRecord p;
        try {
            p = child.getValue(PaymentRecord.class);
        } catch (DatabaseException e) {
            Log.w(TAG, "Skipping malformed payment " + child.getKey(), e);
            return null;
        }
        if (p == null) return null;
        if (p.getPaymentId() == null || p.getPaymentId().isEmpty()) {
            p.setPaymentId(child.getKey());
        }
        return p;
    }
}
