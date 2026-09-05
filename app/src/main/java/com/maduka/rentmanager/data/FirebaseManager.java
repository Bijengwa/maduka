package com.maduka.rentmanager.data;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public final class FirebaseManager {
    private static FirebaseManager instance;
    private final DatabaseReference root;

    private FirebaseManager() {
        root = FirebaseDatabase.getInstance().getReference();
    }

    public static synchronized FirebaseManager get() {
        if (instance == null) instance = new FirebaseManager();
        return instance;
    }

    public DatabaseReference root() { return root; }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(String message);
    }
}
