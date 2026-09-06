package com.maduka.rentmanager.data;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;

import java.util.ArrayList;
import java.util.List;

/** Read-only for this build: the payments/ node is already written to by
 * TenantRepository.registerTenant (one PaymentRecord per tenant registration/renewal). This
 * repository deliberately does NOT add record/confirm/reject methods, since Admin has no
 * payment-recording UI in this build and nothing here should pretend otherwise. */
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
