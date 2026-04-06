package com.example.abacus_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BrowseEntrantsFragment
 *
 * Launched from OrganizerManageFragment via "Send Invites" on the waitlist view.
 * Always receives ARG_EVENT_ID + ARG_EVENT_TITLE + ARG_IS_PRIVATE.
 *
 * - Private event  → tapping a user offers "Invite as Entrant" or "Invite as Co-Organizer"
 * - Public event   → tapping a user goes straight to co-organizer invite only
 */
public class BrowseEntrantsFragment extends Fragment {

    public static final String ARG_EVENT_ID    = "eventId";
    public static final String ARG_EVENT_TITLE = "eventTitle";
    public static final String ARG_IS_PRIVATE  = "isPrivate";

    private AdminViewModel       adminViewModel;
    private ManageEventViewModel manageEventViewModel;
    private RecyclerView         recyclerView;
    private OrganizerEntrantAdapter adapter;
    private TextInputEditText    etSearch;
    private View                 layoutEmpty;

    private List<User> allUsers      = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();

    private String  eventId    = null;
    private String  eventTitle = null;
    private boolean isPrivate  = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.browse_entrants_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            eventId    = getArguments().getString(ARG_EVENT_ID,    null);
            eventTitle = getArguments().getString(ARG_EVENT_TITLE, null);
            isPrivate  = getArguments().getBoolean(ARG_IS_PRIVATE, false);
        }

        adminViewModel       = new ViewModelProvider(this).get(AdminViewModel.class);
        manageEventViewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);

        recyclerView = view.findViewById(R.id.rv_entrants);
        etSearch     = view.findViewById(R.id.et_search);
        layoutEmpty  = view.findViewById(R.id.layout_empty);

        TextView tvTitle = view.findViewById(R.id.tv_title);
        if (tvTitle != null) {
            tvTitle.setText(isPrivate ? "Send Invites" : "Invite Co-Organizer");
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrganizerEntrantAdapter(filteredUsers, this::showInviteOptions);
        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilter(s.toString()); }
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(
                                        android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                etSearch.clearFocus();
                return true;
            }
            return false;
        });

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> requireActivity().onBackPressed());
        }

        observeViewModel();
        adminViewModel.loadProfiles();
    }

    private void observeViewModel() {
        adminViewModel.getProfiles().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                allUsers.clear();
                for (User u : users) {
                    if (!u.isDeleted()) allUsers.add(u);
                }
                applyFilter(etSearch.getText() != null
                        ? etSearch.getText().toString() : "");
            }
        });
    }

    private void applyFilter(String query) {
        String q = query.toLowerCase().trim();
        filteredUsers.clear();
        if (q.isEmpty()) {
            filteredUsers.addAll(allUsers);
        } else {
            for (User u : allUsers) {
                boolean match =
                        (u.getName()  != null && u.getName().toLowerCase().contains(q))
                                || (u.getEmail() != null && u.getEmail().toLowerCase().contains(q))
                                || (u.getPhone() != null && u.getPhone().contains(q));
                if (match) filteredUsers.add(u);
            }
        }
        adapter.notifyDataSetChanged();
        layoutEmpty.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Invite logic ──────────────────────────────────────────────────────────

    private void showInviteOptions(User user) {
        String[] options = isPrivate
                ? new String[]{"Invite as Entrant", "Invite as Co-Organizer"}
                : new String[]{"Invite as Co-Organizer"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Invite " + (user.getName() != null ? user.getName() : "User"))
                .setItems(options, (dialog, which) -> {
                    if (isPrivate) {
                        if (which == 0) confirmEntrantInvite(user);
                        else confirmCoOrganizerInvite(user);
                    } else {
                        confirmCoOrganizerInvite(user);
                    }
                })
                .show();
    }

    private void confirmEntrantInvite(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Invite as Entrant")
                .setMessage("Invite " + (user.getName() != null ? user.getName() : "this user")
                        + " to the waitlist for \"" + eventTitle + "\"?")
                .setPositiveButton("Send", (dialog, which) -> {
                    manageEventViewModel.inviteToPrivateEvent(eventId, eventTitle, user);
                    Toast.makeText(getContext(),
                            "Waitlist invite sent to " + user.getName(),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmCoOrganizerInvite(User user) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Invite as Co-Organizer")
                .setMessage("Send a co-organizer invite to "
                        + (user.getName() != null ? user.getName() : "this user")
                        + " for \"" + eventTitle + "\"?")
                .setPositiveButton("Send", (dialog, which) -> {
                    manageEventViewModel.sendCoOrganizerInvite(eventId, eventTitle, user);
                    Toast.makeText(getContext(),
                            "Co-organizer invite sent to " + user.getName(),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}