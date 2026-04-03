package com.example.abacus_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
 * OrganizerLogsFragment
 * Now used as "Browse Entrants" for organizers.
 * Allows searching and inviting entrants to private events or as co-organizers.
 */
public class OrganizerLogsFragment extends Fragment {

    private AdminViewModel adminViewModel;
    private ManageEventViewModel manageEventViewModel;
    private RecyclerView recyclerView;
    private OrganizerEntrantAdapter adapter;
    private TextInputEditText etSearch; // updated from EditText
    private View layoutEmpty;

    private List<User> allUsers = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.organizer_logs_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adminViewModel = new ViewModelProvider(this).get(AdminViewModel.class);
        manageEventViewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);

        recyclerView = view.findViewById(R.id.rv_entrants);
        etSearch = view.findViewById(R.id.et_search);
        layoutEmpty = view.findViewById(R.id.layout_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OrganizerEntrantAdapter(filteredUsers, this::showInviteOptions);
        recyclerView.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                applyFilter(s.toString());
            }
        });

        // Dismiss keyboard and clear focus when search action is pressed
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager)
                                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
                etSearch.clearFocus();
                return true;
            }
            return false;
        });

        observeViewModel();
        adminViewModel.loadProfiles();
    }

    private void observeViewModel() {
        adminViewModel.getProfiles().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                allUsers.clear();
                // Only show entrants/users that are not deleted
                for (User u : users) {
                    if (!u.isDeleted()) {
                        allUsers.add(u);
                    }
                }
                applyFilter(etSearch.getText().toString());
            }
        });
    }

    private void applyFilter(String query) {
        String lowerQuery = query.toLowerCase().trim();
        if (lowerQuery.isEmpty()) {
            filteredUsers.clear();
            filteredUsers.addAll(allUsers);
        } else {
            filteredUsers.clear();
            for (User u : allUsers) {
                boolean match = (u.getName() != null && u.getName().toLowerCase().contains(lowerQuery))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(lowerQuery))
                        || (u.getPhone() != null && u.getPhone().contains(lowerQuery));
                if (match) filteredUsers.add(u);
            }
        }

        adapter.notifyDataSetChanged();
        layoutEmpty.setVisibility(filteredUsers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showInviteOptions(User user) {
        String[] options = {"Invite to Private Event Waitlist", "Invite as Co-Organizer"};
        new AlertDialog.Builder(requireContext())
                .setTitle("Invite " + (user.getName() != null ? user.getName() : "User"))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showPrivateEventPicker(user);
                    } else {
                        inviteAsCoOrganizer(user);
                    }
                })
                .show();
    }

    private void showPrivateEventPicker(User user) {
        // Use Firebase UID (authenticated account) not device UUID
        String organizerId = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser().getUid();

        manageEventViewModel.getEvents().observe(getViewLifecycleOwner(), events -> {
            if (events == null || events.isEmpty()) {
                Toast.makeText(getContext(), "You have no events", Toast.LENGTH_SHORT).show();
                return;
            }

            List<Event> privateEvents = events.stream()
                    .filter(Event::isPrivate)
                    .collect(Collectors.toList());

            if (privateEvents.isEmpty()) {
                Toast.makeText(getContext(), "You have no private events", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] eventTitles = privateEvents.stream().map(Event::getTitle).toArray(String[]::new);

            new AlertDialog.Builder(requireContext())
                    .setTitle("Select Private Event")
                    .setItems(eventTitles, (dialog, which) -> {
                        Event selected = privateEvents.get(which);
                        inviteToWaitlist(user, selected);
                    })
                    .show();
        });

        if (organizerId != null) {
            manageEventViewModel.loadOrganizerEvents(organizerId);
        }
    }

    private void inviteToWaitlist(User user, Event event) {
        // Logic to add user to registrations collection with status 'invited' or 'waitlisted'
        // For now, we'll just show a toast as the actual Firestore logic for manual invite
        // might need a specific repository method.
        Toast.makeText(getContext(), "Invited " + user.getName() + " to " + event.getTitle(), Toast.LENGTH_SHORT).show();
    }

    private void inviteAsCoOrganizer(User user) {
        // Logic to update user's role or add to a co-organizers sub-collection
        Toast.makeText(getContext(), user.getName() + " invited as co-organizer", Toast.LENGTH_SHORT).show();
    }
}