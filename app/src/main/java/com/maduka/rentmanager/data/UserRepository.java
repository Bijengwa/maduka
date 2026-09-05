package com.maduka.rentmanager.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
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
    private final FirebaseManager fb = FirebaseManager.get();

    public interface AdminsListener { void onAdmins(List<AdminUser> admins); void onError(String message); }

    /** All registered admin accounts, live-updating, sorted by name. */
    public void observeAdmins(AdminsListener listener) {
        fb.root().child(FirebaseSchema.ADMINS).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<AdminUser> admins = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    AdminUser a = child.getValue(AdminUser.class);
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
        });
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
