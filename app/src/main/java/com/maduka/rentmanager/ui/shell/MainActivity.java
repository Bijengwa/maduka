package com.maduka.rentmanager.ui.shell;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.AuthRepository;
import com.maduka.rentmanager.data.model.UserRole;
import com.maduka.rentmanager.ui.admin.dashboard.AdminDashboardFragment;
import com.maduka.rentmanager.ui.admin.notifications.AdminNotificationsFragment;
import com.maduka.rentmanager.ui.admin.properties.PropertiesFragment;
import com.maduka.rentmanager.ui.admin.reports.ReportsFragment;
import com.maduka.rentmanager.ui.admin.tenants.TenantsFragment;
import com.maduka.rentmanager.ui.common.ChangePasswordDialog;
import com.maduka.rentmanager.ui.login.LoginActivity;
import com.maduka.rentmanager.ui.superadmin.users.UsersFragment;
import com.maduka.rentmanager.ui.tenant.dashboard.TenantDashboardFragment;
import com.maduka.rentmanager.ui.tenant.details.TenantDetailsFragment;
import com.maduka.rentmanager.ui.tenant.history.TenantHistoryFragment;
import com.maduka.rentmanager.ui.tenant.notifications.TenantNotificationsFragment;
import com.maduka.rentmanager.ui.tenant.payments.TenantPaymentsFragment;
import com.maduka.rentmanager.util.LocaleHelper;
import com.maduka.rentmanager.util.Prefs;
import android.widget.Button;
import android.widget.ImageButton;

public class MainActivity extends AppCompatActivity {
    private UserRole role;
    private BottomNavigationView bottomNav;

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

        bottomNav = findViewById(R.id.bottomNav);
        bottomNav.inflateMenu(menuFor(role));
        bottomNav.setOnItemSelectedListener(item -> {
            showFragment(fragmentFor(item.getItemId()));
            return true;
        });
        showFragment(fragmentFor(bottomNav.getMenu().getItem(0).getItemId()));

        Button btnLanguage = findViewById(R.id.btnLanguage);
        Prefs prefs = Prefs.get(this);
        btnLanguage.setText(prefs.language().equals("sw") ? "SW" : "EN");
        btnLanguage.setOnClickListener(v -> {
            String next = prefs.language().equals("sw") ? "en" : "sw";
            prefs.setLanguage(next);
            recreate();
        });

        ImageButton btnChangePassword = findViewById(R.id.btnChangePassword);
        btnChangePassword.setOnClickListener(v -> new ChangePasswordDialog().show(getSupportFragmentManager(), "change_password"));

        ImageButton btnSignOut = findViewById(R.id.btnSignOut);
        btnSignOut.setOnClickListener(v -> {
            new AuthRepository().signOut();
            Prefs.get(this).setRememberMe(false, null, null);
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
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
        if (itemId == R.id.nav_properties) return new PropertiesFragment();
        if (itemId == R.id.nav_tenants) return new TenantsFragment();
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
