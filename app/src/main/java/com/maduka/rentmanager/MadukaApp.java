package com.maduka.rentmanager;

import android.app.Application;
import com.google.firebase.database.FirebaseDatabase;
import com.maduka.rentmanager.notifications.NotificationHelper;

public class MadukaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        NotificationHelper.createChannels(this);
    }
}
