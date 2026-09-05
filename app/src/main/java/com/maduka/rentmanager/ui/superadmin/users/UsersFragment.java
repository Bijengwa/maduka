package com.maduka.rentmanager.ui.superadmin.users;

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

import com.maduka.rentmanager.R;
import com.maduka.rentmanager.data.UserRepository;
import com.maduka.rentmanager.data.model.AdminUser;

import java.util.List;

/** Only reachable by Super Admin (bottom_nav_super_admin.xml has nav_users, Admin's menu
 * doesn't) - so unlike PropertiesFragment there is no role branching needed here. */
public class UsersFragment extends Fragment {
    private final UserRepository userRepository = new UserRepository();

    private RecyclerView recyclerAdmins;
    private TextView tvEmpty;
    private Button btnAddAdmin;
    private UserAdapter adapter;

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

        btnAddAdmin.setOnClickListener(v ->
                new RegisterAdminSheet().show(getChildFragmentManager(), "register_admin"));

        userRepository.observeAdmins(new UserRepository.AdminsListener() {
            @Override
            public void onAdmins(List<AdminUser> admins) {
                if (!isAdded()) return;
                adapter.submitList(admins);
                boolean empty = admins.isEmpty();
                recyclerAdmins.setVisibility(empty ? View.GONE : View.VISIBLE);
                tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
