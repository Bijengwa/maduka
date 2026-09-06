package com.maduka.rentmanager.data;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.PaymentRecord;

import java.util.ArrayList;
import java.util.List;

/** Read-only for this session: the payments/ node is already written to by
 * TenantRepository.registerTenant (one PaymentRecord per tenant registration/renewal). This
 * repository only adds the query-scoped read Tenant-self screens need - it deliberately does
 * NOT add record/confirm/reject methods, since Admin has no payment-recording UI this session
 * (Super Admin records/confirms are out of this session's scope) and nothing here should
 * pretend otherwise. */
public class PaymentRepository {
    private final FirebaseManager fb = FirebaseManager.get();

    public interface PaymentsListener { void onPayments(List<PaymentRecord> payments); void onError(String message); }

    /** A tenant's own payment records, live-updating - a real server-side query
     * (orderByChild("tenantUid").equalTo(uid)), not a client-side filter of the full node. */
    public void observePaymentsForTenant(String tenantUid, PaymentsListener listener) {
        Query query = fb.root().child(FirebaseSchema.PAYMENTS).orderByChild("tenantUid").equalTo(tenantUid);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PaymentRecord> payments = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    PaymentRecord p = child.getValue(PaymentRecord.class);
                    if (p != null) payments.add(p);
                }
                payments.sort((a, b) -> Long.compare(b.getPaymentDate(), a.getPaymentDate()));
                listener.onPayments(payments);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        });
    }
}
