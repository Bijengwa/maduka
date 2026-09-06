package com.maduka.rentmanager.ui.admin.notifications;

import android.content.Context;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;

/** Full-screen host for AdminNotificationsFragment, reached via the top-bar bell for the one
 * role with no bottom-nav Notifications tab (Super Admin - its 5 tabs are Dashboard/Properties/
 * Tenants/Reports/Users). Admin and Tenant instead just select their existing bottom-nav
 * Notifications tab when the bell is tapped; this Activity is Super-Admin-only in practice. */
public class NotificationsActivity extends AppCompatActivity {
    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        EdgeToEdge.applyTopInset(findViewById(R.id.notificationsTopBar));
        EdgeToEdge.applyBottomInset(findViewById(R.id.notificationsRoot));

        ImageButton btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());
    }
}
