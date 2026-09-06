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
import com.maduka.rentmanager.ui.settings.SettingsActivity;
import com.maduka.rentmanager.ui.superadmin.users.UsersFragment;
import com.maduka.rentmanager.ui.tenant.dashboard.TenantDashboardFragment;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.ui.tenant.notifications.TenantNotificationsFragment;
import com.maduka.rentmanager.ui.tenant.payments.TenantPaymentsFragment;
import com.maduka.rentmanager.util.EdgeToEdge;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import android.widget.Button;
import android.widget.ImageButton;

public class MainActivity extends AppCompatActivity {
    private static final String STATE_SELECTED_NAV_ID = "selected_nav_id";

    private UserRole role;
    private BottomNavigationView bottomNav;
    private int selectedNavId;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.get(newBase).language()));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String roleName = getIntent().getStringExtra(LoginActivity.EXTRA_ROLE);
        role = roleName != null ? UserRole.valueOf(roleName) : UserRole.ADMIN;

        View topBar = findViewById(R.id.topBar);
        EdgeToEdge.applyTopInset(topBar);

        bottomNav = findViewById(R.id.bottomNav);
        EdgeToEdge.applyBottomInset(bottomNav);
        bottomNav.inflateMenu(menuFor(role));
        bottomNav.setOnItemSelectedListener(item -> {
            selectedNavId = item.getItemId();
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
        btnNotifications.setOnClickListener(v -> {
            if (role == UserRole.SUPER_ADMIN) {
                // Super Admin's 5 tabs (Dashboard/Properties/Tenants/Reports/Users) have no
                // Notifications destination - the bell opens the same content full-screen.
                startActivity(new Intent(this, com.maduka.rentmanager.ui.admin.notifications.NotificationsActivity.class));
            } else {
                bottomNav.setSelectedItemId(R.id.nav_notifications);
            }
        });

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
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
        if (itemId == R.id.nav_dashboard) return role == UserRole.TENANT ? new TenantDashboardFragment() : new AdminDashboardFragment();
        if (itemId == R.id.nav_properties) return PropertiesFragment.newInstance(role);
        if (itemId == R.id.nav_tenants) return TenantsFragment.newInstance(role);
        if (itemId == R.id.nav_reports) return new ReportsFragment();
        if (itemId == R.id.nav_notifications) return role == UserRole.TENANT ? new TenantNotificationsFragment() : new AdminNotificationsFragment();
        if (itemId == R.id.nav_users) return new UsersFragment();
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
