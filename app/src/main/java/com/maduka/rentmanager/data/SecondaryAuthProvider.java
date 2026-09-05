package com.maduka.rentmanager.data;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

public final class SecondaryAuthProvider {
    private static final String SECONDARY_APP_NAME = "SecondaryAuthApp";

    private SecondaryAuthProvider() {}

    /** Returns a FirebaseAuth bound to a separate FirebaseApp instance, so
     * calling createUserWithEmailAndPassword on it never disturbs the
     * primary app's (the currently signed-in Super Admin's) session. */
    public static FirebaseAuth get() {
        FirebaseApp secondaryApp;
        try {
            secondaryApp = FirebaseApp.getInstance(SECONDARY_APP_NAME);
        } catch (IllegalStateException notYetCreated) {
            FirebaseOptions options = FirebaseApp.getInstance().getOptions();
            secondaryApp = FirebaseApp.initializeApp(
                    FirebaseApp.getInstance().getApplicationContext(), options, SECONDARY_APP_NAME);
        }
        return FirebaseAuth.getInstance(secondaryApp);
    }
}
