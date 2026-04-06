package com.example.abacus_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

/**
 * BrowseEntrantsFragment
 *
 * <p>Launched from {@link OrganizerManageFragment} via the "Send Invites" button on the
 * waitlist view. Navigation to this fragment is blocked entirely when the event has ended,
 * so this fragment only needs to handle the registration-period and role-based invite rules.
 *
 * <p>Always receives {@link #ARG_EVENT_ID}, {@link #ARG_EVENT_TITLE}, {@link #ARG_IS_PRIVATE},
 * {@link #ARG_REG_END_MS}, and {@link #ARG_EVENT_END_MS} as arguments.
 *
 * <ul>
 *   <li>If registration is closed, only co-organizer invites are available.</li>
 *   <li>If the event is private and registration is open, both entrant and co-organizer
 *       invites are available, unless the selected user is already an organizer.</li>
 *   <li>If the event is public, only co-organizer invites are available.</li>
 * </ul>
 */
public class BrowseEntrantsFragment extends Fragment {

    /** Firestore document ID of the event. */
    public static final String ARG_EVENT_ID     = "eventId";

    /** Display title of the event, shown in dialogs and the screen header. */
    public static final String ARG_EVENT_TITLE  = "eventTitle";

    /** Whether the event is private; controls entrant invite availability. */
    public static final String ARG_IS_PRIVATE   = "isPrivate";

    /**
     * Unix epoch milliseconds for when registration closes.
     * A value of {@code 0} means no registration deadline is set.
     */
    public static final String ARG_REG_END_MS   = "regEndMs";

    /**
     * Unix epoch milliseconds for when the event ends. Passed through from
     * {@link OrganizerManageFragment} but not used for gating here — navigation
     * is already blocked upstream when the event is over.
     */
    public static final String ARG_EVENT_END_MS = "eventEndMs";

    private AdminViewModel          adminViewModel;
    private ManageEventViewModel    manageEventViewModel;
    private RecyclerView            recyclerView;
    private OrganizerEntrantAdapter adapter;
    private TextInputEditText       etSearch;
    private View                    layoutEmpty;

    private List<User> allUsers      = new ArrayList<>();
    private List<User> filteredUsers = new ArrayList<>();

    private String  eventId    = null;
    private String  eventTitle = null;
    private boolean isPrivate  = false;
    private long    regEndMs   = 0L;

    /**
     * Inflates the fragment layout.
     *
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.browse_entrants_fragment, container, false);
    }

    /**
     * Initialises views, reads arguments, wires up the search field, back button,
     * and adapter.
     *
     * {@inheritDoc}
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (getArguments() != null) {
            eventId   = getArguments().getString(ARG_EVENT_ID,    null);
            eventTitle = getArguments().getString(ARG_EVENT_TITLE, null);
            isPrivate  = getArguments().getBoolean(ARG_IS_PRIVATE, false);
            regEndMs   = getArguments().getLong(ARG_REG_END_MS,   0L);
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

    /**
     * Observes the list of all profiles from {@link AdminViewModel}. Excludes soft-deleted
     * users and triggers a fresh filter pass whenever the list updates.
     */
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

    /**
     * Filters {@link #allUsers} by the given query string, matching against name,
     * email, and phone. Updates {@link #filteredUsers}, notifies the adapter, and
     * toggles the empty-state view.
     *
     * @param query The current search query; an empty string shows all users.
     */
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

    /**
     * Determines which invite options are available for the tapped user and presents
     * an {@link AlertDialog} with those options.
     *
     * <p>The "Invite as Entrant" option is only shown when all three conditions hold:
     * <ol>
     *   <li>The event is private ({@link #isPrivate} is {@code true}).</li>
     *   <li>The registration period has not yet closed ({@link #regEndMs} is {@code 0}
     *       or the current time is at or before {@link #regEndMs}).</li>
     *   <li>The selected user's role is not {@code "organizer"}.</li>
     * </ol>
     *
     * <p>When registration is closed but the user is not an organizer, the dialog title
     * appends {@code "(Registration closed)"} so the organizer understands why the
     * entrant option is absent.
     *
     * @param user The user tapped in the list.
     */
    private void showInviteOptions(User user) {
        long now = System.currentTimeMillis();
        boolean regOpen     = regEndMs == 0 || now <= regEndMs;
        boolean isOrganizer = "organizer".equals(user.getRole());

        boolean canInviteAsEntrant = isPrivate && regOpen && !isOrganizer;

        String[] options = canInviteAsEntrant
                ? new String[]{"Invite as Entrant", "Invite as Co-Organizer"}
                : new String[]{"Invite as Co-Organizer"};

        String dialogTitle = "Invite " + (user.getName() != null ? user.getName() : "User");
        if (isPrivate && !regOpen && !isOrganizer) {
            dialogTitle += " (Registration closed)";
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(dialogTitle)
                .setItems(options, (dialog, which) -> {
                    if (canInviteAsEntrant && which == 0) confirmEntrantInvite(user);
                    else confirmCoOrganizerInvite(user);
                })
                .show();
    }

    /**
     * Shows a confirmation dialog before sending a waitlist invite to a private event.
     * On confirmation, delegates to
     * {@link ManageEventViewModel#inviteToPrivateEvent(String, String, User)}.
     *
     * @param user The user to invite as an entrant.
     */
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

    /**
     * Shows a confirmation dialog before sending a co-organizer invite.
     * On confirmation, delegates to
     * {@link ManageEventViewModel#sendCoOrganizerInvite(String, String, User)}.
     *
     * @param user The user to invite as a co-organizer.
     */
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