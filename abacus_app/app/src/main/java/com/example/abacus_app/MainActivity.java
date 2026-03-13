/**
 * MainActivity.java
 *
 * Role: The single Activity that hosts all fragments via the Navigation
 * component. Responsible for loading and displaying the event browse list,
 * applying keyword and date filters, managing bottom navigation, and
 * routing to event detail, profile, QR scan, and organizer/admin screens.
 *
 * Design pattern: Activity as host. Fragment navigation is handled through
 * the Jetpack NavController. No ViewModel is used — filter state and event
 * data are held directly in the Activity, consistent with the project's
 * architecture decisions.
 *
 * Outstanding issues:
 * - The event list is not paginated; very large event collections may cause
 *   performance issues.
 * - Role detection makes a Firestore read on every launch; could be cached
 *   locally once the UserRepository stabilizes.
 * - Guest users see the full event list but cannot join waitlists; the UI
 *   does not currently indicate guest-mode restrictions on the browse screen.
 */
package com.example.abacus_app;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchBar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private FragmentContainerView navHostFragment;
    private View homeContent;
    private AppBarLayout appBar;
    private BottomNavigationView bottomNav;

    private String userRole = "entrant";

    private String activeKeyword = "";
    private String activeDate    = "";
    private RecyclerView recyclerView;
    private LinearLayout layoutEmptyState;

    private final List<Event> allEvents = new ArrayList<>();

    private EventRepository eventRepository;
    private UserRepository userRepository;
    private boolean isGuest;

    private ListenerRegistration eventsListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UserLocalDataSource localDataSource = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);
        userRepository.initializeUserAsync();

        isGuest = getIntent().getBooleanExtra("isGuest", false);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        android.util.Log.d("MainActivity", "isGuest from intent: " + isGuest);
        if (currentUser != null) {
            android.util.Log.d("MainActivity", "Current user: " + currentUser.getUid() +
                    " (Anonymous: " + currentUser.isAnonymous() +
                    ", Email: " + currentUser.getEmail() + ")");
        } else {
            android.util.Log.d("MainActivity", "No current user");
        }

        if (!isGuest && currentUser == null) {
            goToSplash();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(80, 255, 255, 255));
        } else {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(180, 255, 255, 255));
        }

        homeContent     = findViewById(R.id.home_content);
        navHostFragment = findViewById(R.id.nav_host_fragment);
        appBar          = findViewById(R.id.app_bar);
        bottomNav       = findViewById(R.id.bottom_nav);

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        SearchBar searchBar = findViewById(R.id.search_bar);
        setSearchBarTextColor(searchBar);

        ImageButton btnProfile = findViewById(R.id.btn_profile);
        btnProfile.setOnClickListener(v -> showFragment(R.id.mainProfileFragment, false));

        ImageButton btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> showFragment(R.id.mainQrScanFragment, false));

        recyclerView = findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        layoutEmptyState = findViewById(R.id.layout_empty_state);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navHostFragment.getVisibility() == View.VISIBLE) {
                    if (!navController.popBackStack()) showHome();
                } else {
                    finish();
                }
            }
        });

        ImageButton btnFilter = findViewById(R.id.btn_filter);
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());

        fetchRoleAndSetupNav(localDataSource);

        eventRepository = new EventRepository();
        loadEventsFromFirestore();
    }

    // ── Role & Nav ────────────────────────────────────────────────────────────

    /**
     * Fetches the current user's role from Firestore and sets up the bottom
     * navigation menu accordingly. Falls back to "entrant" if the fetch fails
     * or if no UUID is available (guest mode).
     *
     * @param localDataSource used to retrieve the locally stored UUID
     */
    private void fetchRoleAndSetupNav(UserLocalDataSource localDataSource) {
        String uuid = localDataSource.getUUIDSync();

        if (uuid != null) {
            FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(uuid)
                    .get()
                    .addOnSuccessListener(doc -> {
                        String role = doc.getString("role");
                        userRole = (role != null) ? role : "entrant";
                        android.util.Log.d("MainActivity", "Role from Firestore: " + userRole + " for UUID: " + uuid);
                        setupBottomNav();
                        applyFilters();
                    })
                    .addOnFailureListener(e -> {
                        android.util.Log.w("MainActivity", "Firestore role fetch failed", e);
                        String intentRole = getIntent().getStringExtra("role");
                        userRole = (intentRole != null) ? intentRole : "entrant";
                        setupBottomNav();
                    });
        } else {
            userRole = "entrant";
            setupBottomNav();
        }
    }

    /**
     * Configures the bottom navigation bar based on the user's role.
     * Organizers and admins see a tools/logs menu; entrants see the
     * standard saved/history/inbox menu.
     */
    private void setupBottomNav() {
        if ("organizer".equals(userRole) || "admin".equals(userRole)) {
            bottomNav.getMenu().clear();
            bottomNav.inflateMenu(R.menu.menu_bottom_nav_organizer);

            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showHome();
                    return true;
                } else if (id == R.id.nav_tools) {
                    showFragment(R.id.organizerToolsFragment, true);
                    return true;
                } else if (id == R.id.nav_logs) {
                    if ("admin".equals(userRole)) {
                        showFragment(R.id.adminLogsFragment, true);
                    } else {
                        showFragment(R.id.organizerLogsFragment, true);
                    }
                    return true;
                } else if (id == R.id.nav_notifications) {
                    showFragment(R.id.nav_inbox, true);
                    return true;
                }
                return false;
            });

        } else {
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showHome();
                    return true;
                } else if (id == R.id.nav_saved) {
                    showFragment(R.id.nav_saved, true);
                    return true;
                } else if (id == R.id.nav_history) {
                    showFragment(R.id.nav_history, true);
                    return true;
                } else if (id == R.id.nav_inbox) {
                    showFragment(R.id.nav_inbox, true);
                    return true;
                }
                return false;
            });
        }
    }

    // ── Firestore real-time event loading ─────────────────────────────────────

    /**
     * Attaches a real-time Firestore snapshot listener to the events collection.
     * Updates allEvents and re-applies filters on every change.
     * The listener is removed in onDestroy to prevent memory leaks.
     */
    private void loadEventsFromFirestore() {
        showLoadingState();

        eventsListener = FirebaseFirestore.getInstance()
                .collection("events")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        android.util.Log.e("MainActivity", "Listen failed: " + error.getMessage());
                        applyFilters();
                        return;
                    }
                    if (querySnapshot == null) return;

                    android.util.Log.d("MainActivity", "Snapshot: " + querySnapshot.size() + " events");
                    allEvents.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        try {
                            Event event = doc.toObject(Event.class);
                            if (event != null) {
                                if (event.getEventId() == null || event.getEventId().isEmpty()) {
                                    event.setEventId(doc.getId());
                                }
                                android.util.Log.d("MainActivity", "Event: " + event.getTitle()
                                        + " | posterUrl: " + event.getPosterImageUrl());
                                allEvents.add(event);
                            }
                        } catch (Exception ex) {
                            android.util.Log.w("MainActivity", "Skipping bad doc: " + doc.getId(), ex);
                        }
                    }
                    applyFilters();
                });
    }

    // ── Filter & Adapter ──────────────────────────────────────────────────────

    /**
     * Filters allEvents by the active keyword, date, and expiry, then binds
     * the result to the RecyclerView via EventAdapter. Shows an empty state
     * view if no events match.
     *
     * Filtering rules:
     * - Keyword: matches event title or description (case-insensitive).
     * - Date: matches events whose registrationStart falls on the selected date.
     * - Expiry: events whose registrationEnd has passed are always hidden.
     *   (US 01.01.03 — entrants only see active, open events)
     */
    private void applyFilters() {
        List<Event> filtered = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date now = new Date();

        for (Event event : allEvents) {
            String title       = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
            String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";

            boolean keywordMatch = activeKeyword.isEmpty()
                    || title.contains(activeKeyword)
                    || description.contains(activeKeyword);

            boolean dateMatch = true;
            if (!activeDate.isEmpty() && event.getRegistrationStart() != null) {
                String eventDate = sdf.format(event.getRegistrationStart().toDate());
                dateMatch = eventDate.equals(activeDate);
            }

            // Hide events whose registration period has ended
            boolean notExpired = true;
            if (event.getRegistrationEnd() != null) {
                notExpired = event.getRegistrationEnd().toDate().after(now);
            }

            if (keywordMatch && dateMatch && notExpired) filtered.add(event);
        }

        boolean isAdmin = "admin".equals(userRole);

        recyclerView.setAdapter(new EventAdapter(
                filtered,
                eventTitle -> {
                    String eventId = "";
                    for (Event e : filtered) {
                        if (e.getTitle() != null && e.getTitle().equals(eventTitle)) {
                            eventId = e.getEventId() != null ? e.getEventId() : "";
                            break;
                        }
                    }
                    Bundle args = new Bundle();
                    args.putString(EventDetailsFragment.ARG_EVENT_TITLE, eventTitle);
                    args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);
                    showFragment(R.id.eventDetailsFragment, false, args);
                },
                isAdmin ? this::confirmDeleteEvent : null,
                isAdmin
        ));

        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
            if (tvEmpty != null) {
                boolean hasFilters = !activeKeyword.isEmpty() || !activeDate.isEmpty();
                tvEmpty.setText(hasFilters ? "No matching events found" : "No events available");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    // ── Admin event deletion ──────────────────────────────────────────────────

    /**
     * Shows a confirmation dialog before soft-deleting an event.
     * Only called when the current user has the admin role.
     *
     * @param event the event to be deleted
     */
    private void confirmDeleteEvent(Event event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Event")
                .setMessage("Remove \"" + event.getTitle() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> softDeleteEvent(event))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Soft-deletes an event by setting its isDeleted field to true in Firestore.
     * The event is immediately removed from the local list and the adapter refreshed.
     *
     * @param event the event to soft-delete
     */
    private void softDeleteEvent(Event event) {
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            android.util.Log.e("MainActivity", "Cannot delete event — eventId is null");
            return;
        }
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(event.getEventId())
                .update("isDeleted", true)
                .addOnSuccessListener(unused -> {
                    allEvents.remove(event);
                    applyFilters();
                })
                .addOnFailureListener(e ->
                        android.util.Log.e("MainActivity", "Failed to delete event: " + e.getMessage()));
    }

    // ── Bottom sheet filter ───────────────────────────────────────────────────

    /**
     * Displays the filter bottom sheet dialog. Allows the user to filter events
     * by keyword and/or registration start date. Applies or clears filters on
     * button tap and dismisses the dialog.
     */
    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.fragment_filter_bottom_sheet);

        TextInputEditText etKeyword = dialog.findViewById(R.id.et_keyword);
        Button btnPickDate          = dialog.findViewById(R.id.btn_pick_date);
        Button btnApply             = dialog.findViewById(R.id.btn_apply_filters);
        Button btnClear             = dialog.findViewById(R.id.btn_clear_filters);

        if (etKeyword != null && !activeKeyword.isEmpty()) etKeyword.setText(activeKeyword);

        final String[] pickedDate = {activeDate};
        if (btnPickDate != null && !activeDate.isEmpty()) btnPickDate.setText(activeDate);

        if (btnPickDate != null) {
            btnPickDate.setOnClickListener(v -> {
                Calendar cal = Calendar.getInstance();
                new DatePickerDialog(this, (view, year, month, day) -> {
                    pickedDate[0] = String.format(Locale.getDefault(),
                            "%04d-%02d-%02d", year, month + 1, day);
                    btnPickDate.setText(pickedDate[0]);
                }, cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)).show();
            });
        }

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                activeKeyword = etKeyword != null && etKeyword.getText() != null
                        ? etKeyword.getText().toString().trim().toLowerCase() : "";
                activeDate = pickedDate[0];
                applyFilters();
                dialog.dismiss();
            });
        }

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                activeKeyword = "";
                activeDate    = "";
                applyFilters();
                dialog.dismiss();
            });
        }

        dialog.show();
    }

    /**
     * Shows a loading placeholder in the event list area while Firestore
     * data is being fetched.
     */
    private void showLoadingState() {
        recyclerView.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
        if (tvEmpty != null) tvEmpty.setText("Loading events...");
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    /**
     * Navigates to a fragment without passing arguments.
     *
     * @param destinationId nav graph destination ID
     * @param showBottomNav whether to keep the bottom nav visible
     */
    public void showFragment(int destinationId, boolean showBottomNav) {
        showFragment(destinationId, showBottomNav, null);
    }

    /**
     * Navigates to a fragment, optionally passing a Bundle of arguments.
     * Hides the home content and app bar during fragment display.
     *
     * @param destinationId nav graph destination ID
     * @param showBottomNav whether to keep the bottom nav visible
     * @param args          optional Bundle passed to the destination fragment
     */
    public void showFragment(int destinationId, boolean showBottomNav, Bundle args) {
        clearBackStack();
        navHostFragment.setVisibility(View.VISIBLE);
        homeContent.setVisibility(View.GONE);
        appBar.setVisibility(View.GONE);
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        navController.navigate(destinationId, args);
    }

    /**
     * Returns to the home event browse screen, restoring the app bar and
     * bottom navigation.
     */
    public void showHome() {
        clearBackStack();
        navHostFragment.setVisibility(View.GONE);
        homeContent.setVisibility(View.VISIBLE);
        appBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
    }

    /**
     * Pops all entries off the navigation back stack.
     */
    private void clearBackStack() {
        while (navController.getCurrentBackStackEntry() != null) {
            if (!navController.popBackStack()) break;
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventsListener != null) eventsListener.remove();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Recursively sets the text and hint color of all TextViews inside a
     * ViewGroup to black. Used to fix the SearchBar text color.
     *
     * @param viewGroup the root ViewGroup to traverse
     */
    private void setSearchBarTextColor(ViewGroup viewGroup) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View child = viewGroup.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(ContextCompat.getColor(this, R.color.black));
                ((TextView) child).setHintTextColor(ContextCompat.getColor(this, R.color.black));
            } else if (child instanceof ViewGroup) {
                setSearchBarTextColor((ViewGroup) child);
            }
        }
    }

    /**
     * Returns the NavController used for fragment navigation.
     *
     * @return the NavController
     */
    public NavController getNavController() { return navController; }

    /**
     * Returns the current user's role ("entrant", "organizer", or "admin").
     *
     * @return the user role string
     */
    public String getUserRole() { return userRole; }

    /**
     * Called by EventDetailsFragment when a guest user tries to join a waitlist.
     * Shows a prompt to sign in or register.
     */
    public void onGuestJoinAttempt() {
        showLoginPrompt("Sign in to join events.");
    }

    /**
     * Displays a dialog prompting the user to sign in or register.
     *
     * @param message the message to display in the dialog body
     */
    private void showLoginPrompt(String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sign in required")
                .setMessage(message)
                .setPositiveButton("Sign In", (d, w) ->
                        startActivity(new Intent(this, LoginActivity.class)))
                .setNeutralButton("Register", (d, w) ->
                        startActivity(new Intent(this, RegisterActivity.class)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * Redirects to the SplashActivity and clears the back stack.
     * Called when no authenticated user is found and the app is not in guest mode.
     */
    private void goToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}