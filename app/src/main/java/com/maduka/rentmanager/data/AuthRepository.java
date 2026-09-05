package com.maduka.rentmanager.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.util.LoginKeyUtil;

public class AuthRepository {
    private final FirebaseManager fb = FirebaseManager.get();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private UserRole cachedRole;

    public static class AuthResult {
        public final String uid;
        public final UserRole role;
        public AuthResult(String uid, UserRole role) { this.uid = uid; this.role = role; }
    }

    /**
     * Resolves the free-text identifier (username, email, or phone) against
     * the public login_index/{role} node to find the Firebase Auth email,
     * then signs in with that email + the entered password.
     */
    public void signIn(UserRole role, String identifier, String password, FirebaseManager.Callback<AuthResult> cb) {
        String key = LoginKeyUtil.sanitize(identifier);
        fb.root().child(FirebaseSchema.LOGIN_INDEX).child(role.node).child(key)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        String authEmail = snapshot.getValue(String.class);
                        if (authEmail == null) {
                            cb.onError("No account found for that role and identifier.");
                            return;
                        }
                        auth.signInWithEmailAndPassword(authEmail, password)
                                .addOnSuccessListener(result -> {
                                    cachedRole = role;
                                    cb.onSuccess(new AuthResult(result.getUser().getUid(), role));
                                })
                                .addOnFailureListener(e -> cb.onError(e.getMessage()));
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        cb.onError(error.getMessage());
                    }
                });
    }

    public void signOut() {
        auth.signOut();
        cachedRole = null;
    }

    public void changePassword(String newPassword, FirebaseManager.Callback<Void> cb) {
        if (auth.getCurrentUser() == null) { cb.onError("Not signed in."); return; }
        auth.getCurrentUser().updatePassword(newPassword)
                .addOnSuccessListener(v -> cb.onSuccess(null))
                .addOnFailureListener(e -> cb.onError(e.getMessage()));
    }

    public String currentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public UserRole cachedRole() { return cachedRole; }
    public void setCachedRole(UserRole role) { this.cachedRole = role; }
}
