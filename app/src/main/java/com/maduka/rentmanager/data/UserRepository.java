package com.maduka.rentmanager.data;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.AdminUser;
import com.maduka.rentmanager.data.model.PresenceStatus;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.LoginKeyUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserRepository {
    private static final String TAG = "UserRepository";
    private final FirebaseManager fb = FirebaseManager.get();

    public interface AdminsListener { void onAdmins(List<AdminUser> admins); void onError(String message); }
    public interface NameListener { void onName(String name); }

    /** One-shot lookup of the signed-in Admin/Super Admin's own display name, for greetings like
     * the dashboard's "Karibu, {name}" and for stamping PaymentRecord.recordedByName. Falls back
     * to onName(null) rather than an error - callers should show a generic greeting instead of
     * failing the whole screen over a display name. */
    public void observeCurrentUserName(UserRole role, String uid, NameListener listener) {
        if (uid == null || uid.isEmpty()) { listener.onName(null); return; }
        String node = role == UserRole.SUPER_ADMIN ? FirebaseSchema.SUPER_ADMINS : FirebaseSchema.ADMINS;
        fb.root().child(node).child(uid).child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) { listener.onName(snapshot.getValue(String.class)); }
            @Override
            public void onCancelled(DatabaseError error) { listener.onName(null); }
        });
    }

    /** All registered admin accounts, live-updating, sorted by name. Returns the registration so
     * the caller can detach it in onDestroyView via stopObservingAdmins. */
    public ValueEventListener observeAdmins(AdminsListener listener) {
        ValueEventListener registration = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<AdminUser> admins = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AdminUser a = readAdmin(child);
                    if (a != null) admins.add(a);
                }
                admins.sort((a, b) -> {
                    String an = a.getName() != null ? a.getName() : "";
                    String bn = b.getName() != null ? b.getName() : "";
                    return an.compareToIgnoreCase(bn);
                });
                listener.onAdmins(admins);
            }
            @Override
            public void onCancelled(DatabaseError error) { listener.onError(error.getMessage()); }
        };
        fb.root().child(FirebaseSchema.ADMINS).addValueEventListener(registration);
        return registration;
    }

    public void stopObservingAdmins(ValueEventListener registration) {
        if (registration != null) {
            fb.root().child(FirebaseSchema.ADMINS).removeEventListener(registration);
        }
    }

    /** Malformed individual admin records (bad enum values, wrong field types) are skipped with
     * a warning log rather than crashing the whole list, matching ShopRepository/TenantRepository. */
    private AdminUser readAdmin(DataSnapshot child) {
        AdminUser a;
        try {
            a = child.getValue(AdminUser.class);
        } catch (DatabaseException e) {
            Log.w(TAG, "Skipping malformed admin " + child.getKey(), e);
            return null;
        }
        if (a == null) return null;
        if (a.getUid() == null || a.getUid().isEmpty()) {
            a.setUid(child.getKey());
        }
        return a;
    }

    /** Registers a new Admin account. Account creation happens on a SECONDARY FirebaseAuth
     * instance (see SecondaryAuthProvider) so it never touches the primary FirebaseAuth
     * instance the currently signed-in Super Admin is authenticated on - the Super Admin's own
     * session is left completely undisturbed. The resulting Realtime Database writes
     * (admins/{uid} + login_index/admins/{key}) go through the PRIMARY FirebaseManager.root()
     * reference, i.e. as the Super Admin, who is the one actually authorized to write there. */
    public void registerAdmin(String name, String phone, String email, String password,
                               FirebaseManager.Callback<Void> cb) {
        FirebaseAuth secondaryAuth = SecondaryAuthProvider.get();
        secondaryAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    secondaryAuth.signOut();

                    long now = System.currentTimeMillis();
                    AdminUser admin = new AdminUser();
                    admin.setUid(uid);
                    admin.setName(name);
                    admin.setPhone(phone);
                    admin.setEmail(email);
                    admin.setAuthEmail(email);
                    admin.setStatus(PresenceStatus.YUPO);
                    admin.setCreatedAt(now);
                    admin.setUpdatedAt(now);

                    String loginKey = LoginKeyUtil.sanitize(email);

                    Map<String, Object> writes = new HashMap<>();
                    writes.put("/" + FirebaseSchema.ADMINS + "/" + uid, admin);
                    writes.put("/" + FirebaseSchema.LOGIN_INDEX + "/" + UserRole.ADMIN.node + "/" + loginKey, email);

                    fb.root().updateChildren(writes)
                            .addOnSuccessListener(v -> cb.onSuccess(null))
                            .addOnFailureListener(e -> cb.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }
}
