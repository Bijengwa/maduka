package com.maduka.rentmanager.ui.superadmin.users;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.ValueEventListener;
import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.data.model.AdminUser;
import com.maduka.rentmanager.data.model.UserRole;

import java.util.List;

/** Only reachable by Super Admin (bottom_nav_super_admin.xml has nav_users, Admin's menu
 * doesn't), but RegisterAdminActivity now also carries its own EXTRA_ROLE code-path guard, so
 * this fragment takes a role argument (matching PropertiesFragment/TenantsFragment) to pass
 * through rather than assume. */
public class UsersFragment extends Fragment {
    private static final String ARG_ROLE = "arg_role";

    private final UserRepository userRepository = new UserRepository();
    private UserRole role;

    private RecyclerView recyclerAdmins;
    private TextView tvEmpty;
    private Button btnAddAdmin;
    private UserAdapter adapter;
    private ValueEventListener adminsRegistration;

    public static UsersFragment newInstance(UserRole role) {
        UsersFragment fragment = new UsersFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROLE, role.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        String roleName = args != null ? args.getString(ARG_ROLE) : null;
        role = roleName != null ? UserRole.valueOf(roleName) : UserRole.SUPER_ADMIN;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_users, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerAdmins = view.findViewById(R.id.recyclerAdmins);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        btnAddAdmin = view.findViewById(R.id.btnAddAdmin);

        adapter = new UserAdapter();
        recyclerAdmins.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerAdmins.setAdapter(adapter);

        btnAddAdmin.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), RegisterAdminActivity.class);
            intent.putExtra(RegisterAdminActivity.EXTRA_ROLE, role.name());
            startActivity(intent);
        });

        adminsRegistration = userRepository.observeAdmins(new UserRepository.AdminsListener() {
            @Override
            public void onAdmins(List<AdminUser> admins) {
                if (!canTouchViews()) return;
                adapter.submitList(admins);
                boolean empty = admins.isEmpty();
                recyclerAdmins.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!canTouchViews()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (adminsRegistration != null) {
            userRepository.stopObservingAdmins(adminsRegistration);
            adminsRegistration = null;
        }
        super.onDestroyView();
    }

    private boolean canTouchViews() {
        return isAdded() && getView() != null;
    }
}
