package com.maduka.rentmanager.ui.shell;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.admin.dashboard.AdminDashboardFragment;
import com.maduka.rentmanager.ui.admin.notifications.AdminNotificationsFragment;
import com.maduka.rentmanager.ui.admin.properties.PropertiesFragment;
import com.maduka.rentmanager.ui.admin.reports.ReportsFragment;
import com.maduka.rentmanager.ui.admin.tenants.TenantsFragment;
import com.maduka.rentmanager.ui.login.LoginActivity;
import com.maduka.rentmanager.ui.superadmin.users.UsersFragment;
import com.maduka.rentmanager.ui.tenant.dashboard.TenantDashboardFragment;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.ui.tenant.notifications.TenantNotificationsFragment;
import com.maduka.rentmanager.ui.tenant.payments.TenantPaymentsFragment;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_SELECTED_NAV_ID = "selected_nav_id";

    private UserRole role;
    private BottomNavigationView bottomNav;
    private TextView tvTopBarTitle;
    private TextView tvTopBarSubtitle;
    private int selectedNavId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Required on API 33+ for NotificationHelper.show() (OverdueCheckReceiver) to actually
        // display anything - the manifest declaration alone is not enough on modern Android.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1001);
        }

        String roleName = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        role = roleName != null ? UserRole.valueOf(roleName) : UserRole.ADMIN;

        View topBar = findViewById(R.id.topBar);
        EdgeToEdge.applyTopInset(topBar);
        tvTopBarTitle = findViewById(R.id.tvTopBarTitle);
        tvTopBarSubtitle = findViewById(R.id.tvTopBarSubtitle);

        bottomNav = findViewById(R.id.bottomNav);
        EdgeToEdge.applyBottomInset(bottomNav);
        bottomNav.inflateMenu(menuFor(role));
        bottomNav.setOnItemSelectedListener(item -> {
            selectedNavId = item.getItemId();
            tvTopBarTitle.setText(item.getTitle());
            setTopBarSubtitle(null);
            showFragment(fragmentFor(selectedNavId));
            return true;
        });

        // recreate() (e.g. from the language toggle) passes the tab we were on back in
        // savedInstanceState - restore it instead of always defaulting to the first tab, so
        // BottomNavigationView's own auto-restored "selected" highlight (which Android does
        // for any view with a stable id, independent of this code) stays in sync with which
        // fragment is actually showing.
        selectedNavId = savedInstanceState != null
                ? savedInstanceState.getInt(STATE_SELECTED_NAV_ID, bottomNav.getMenu().getItem(0).getItemId())
                : bottomNav.getMenu().getItem(0).getItemId();
        bottomNav.setSelectedItemId(selectedNavId);
        tvTopBarTitle.setText(bottomNav.getMenu().findItem(selectedNavId).getTitle());
        showFragment(fragmentFor(selectedNavId));

        Button btnLanguage = findViewById(R.id.btnLanguage);
        Prefs prefs = Prefs.get(this);
        btnLanguage.setText(prefs.language().equals("sw") ? "SW" : "EN");
        btnLanguage.setOnClickListener(v -> {
            String next = prefs.language().equals("sw") ? "en" : "sw";
            prefs.setLanguage(next);
            recreate();
        });

        ImageButton btnNotifications = findViewById(R.id.btnNotifications);
        ImageButton btnLock = findViewById(R.id.btnLock);
        ImageButton btnLogout = findViewById(R.id.btnLogout);

        // Lock (change password) and sign-out live in the top bar for every role now - the
        // separate Settings screen (which only ever held these same two actions) is retired.
        btnLock.setOnClickListener(v ->
                new com.maduka.rentmanager.ui.common.ChangePasswordDialog().show(getSupportFragmentManager(), "change_password"));
        btnLogout.setOnClickListener(v -> {
            new com.maduka.rentmanager.data.AuthRepository().signOut();
            Prefs.get(this).setRememberMe(false, null, null);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        // Only Super Admin gets the bell: its 5 tabs (Dashboard/Properties/Tenants/Reports/Users)
        // have no Notifications destination, so the bell opens it full-screen instead. Admin
        // already has a Notifications tab; Tenant has its own equivalent tab too.
        if (role == UserRole.SUPER_ADMIN) {
            btnNotifications.setVisibility(View.VISIBLE);
            btnNotifications.setOnClickListener(v ->
                    startActivity(new Intent(this, com.maduka.rentmanager.ui.admin.notifications.NotificationsActivity.class)));
        }
    }

    /** Lets a hosted fragment show a one-line subtitle under the top bar's title (e.g. "9 units
     * registered"), matching the reference design's title+subtitle top bar on every screen. Pass
     * null to hide it (done automatically on every tab switch). */
    public void setTopBarSubtitle(String subtitle) {
        if (tvTopBarSubtitle == null) return;
        if (subtitle == null || subtitle.isEmpty()) {
            tvTopBarSubtitle.setVisibility(View.GONE);
        } else {
            tvTopBarSubtitle.setText(subtitle);
            tvTopBarSubtitle.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED_NAV_ID, selectedNavId);
    }

    private int menuFor(UserRole role) {
        switch (role) {
            case SUPER_ADMIN: return R.menu.bottom_nav_super_admin;
            case TENANT: return R.menu.bottom_nav_tenant;
            default: return R.menu.bottom_nav_admin;
        }
    }

    private Fragment fragmentFor(int itemId) {
        if (itemId == R.id.nav_dashboard) return role == UserRole.TENANT ? new TenantDashboardFragment() : AdminDashboardFragment.newInstance(role);
        if (itemId == R.id.nav_properties) return PropertiesFragment.newInstance(role);
        if (itemId == R.id.nav_tenants) return TenantsFragment.newInstance(role);
        if (itemId == R.id.nav_reports) return new ReportsFragment();
        if (itemId == R.id.nav_notifications) return role == UserRole.TENANT ? new TenantNotificationsFragment() : new AdminNotificationsFragment();
        if (itemId == R.id.nav_users) return UsersFragment.newInstance(role);
        if (itemId == R.id.nav_payments) return new TenantPaymentsFragment();
        if (itemId == R.id.nav_details) return new TenantDetailsFragment();
        if (itemId == R.id.nav_history) return new TenantHistoryFragment();
        throw new IllegalArgumentException("Unknown nav item: " + itemId);
    }

    private void showFragment(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
