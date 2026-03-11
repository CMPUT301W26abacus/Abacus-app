/**
 * MainActivity.java
 *
 * Role: Entry point and primary controller for the Abacus application.
 * Implements the home screen (event browse list) directly and manages
 * fragment-based navigation for all other screens via a NavHostFragment overlay.
 *
 * Design pattern: The home UI lives directly in activity_main.xml rather than
 * a fragment, with a FragmentContainerView overlay that is shown/hidden when
 * navigating away from or back to the home screen. This keeps the main event
 * browse experience as the persistent base of the app.
 *
 * Outstanding issues:
 * - Role-based UI (admin delete buttons, organizer tools) not yet implemented.
 * - Bottom nav "Saved", "History", "Inbox" fragments are currently empty stubs.
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
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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

    // Full list loaded from Firestore — filtered copy shown in RecyclerView
    private final List<Event> allEvents = new ArrayList<>();

    private EventRepository eventRepository;
    private UserRepository userRepository;
    private boolean isGuest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Teammate's original code (unchanged) ──────────────────────────────

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

        recyclerView     = findViewById(R.id.rv_events);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

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

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (navHostFragment.getVisibility() == View.VISIBLE) {
                    if (!navController.popBackStack()) {
                        showHome();
                    }
                } else {
                    finish();
                }
            }
        });

        ImageButton btnFilter = findViewById(R.id.btn_filter);
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());

        // ── Load real events from Firestore (replaces hardcoded list) ──────────
        eventRepository = new EventRepository();
        loadEventsFromFirestore();
    }

    // ── Firestore event loading ────────────────────────────────────────────────

    /**
     * Fetches all events from Firestore using Himesh's EventRepository.
     * Forces a server fetch (bypasses local cache) so changes in Firestore
     * are always reflected immediately.
     * On success, stores them in allEvents and applies current filters.
     * Shows empty state if Firestore returns nothing or fails.
     */
    private void loadEventsFromFirestore() {
        showLoadingState();

        // Force fetch from server, bypassing Firestore local cache
        FirebaseFirestore.getInstance()
                .collection("events")
                .get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    android.util.Log.d("MainActivity", "Firestore returned " + querySnapshot.size() + " events");
                    allEvents.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        android.util.Log.d("MainActivity", "Event loaded: " + (event != null ? event.getTitle() : "null"));
                        if (event != null) {
                            allEvents.add(event);
                        }
                    }
                    applyFilters();
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("MainActivity", "Failed to load events: " + e.getMessage());
                    applyFilters(); // will show empty state
                });
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.fragment_filter_bottom_sheet);

        TextInputEditText etKeyword  = dialog.findViewById(R.id.et_keyword);
        Button btnPickDate           = dialog.findViewById(R.id.btn_pick_date);
        Button btnApply              = dialog.findViewById(R.id.btn_apply_filters);
        Button btnClear              = dialog.findViewById(R.id.btn_clear_filters);

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
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
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
     * Filters allEvents (real Firestore events) using active keyword and/or date.
     * Keyword matches title and description (case-insensitive).
     * Date matches events whose registrationStart falls on the selected date.
     * Both filters must match when both are active (AND logic).
     */
    private void applyFilters() {
        List<Event> filtered = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

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

            if (keywordMatch && dateMatch) filtered.add(event);
        }

        List<String> titles = new ArrayList<>();
        for (Event e : filtered) titles.add(e.getTitle());

        recyclerView.setAdapter(new EventAdapter(titles, eventTitle -> {
            String eventId = "";
            for (Event e : filtered) {
                if (e.getTitle() != null && e.getTitle().equals(eventTitle)) {
                    eventId = e.getEventId();
                    break;
                }
            }
            Bundle args = new Bundle();
            args.putString(EventDetailsFragment.ARG_EVENT_TITLE, eventTitle);
            args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);
            showFragment(R.id.eventDetailsFragment, false, args);
        }));

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

    /** Shows empty state while Firestore is loading. */
    private void showLoadingState() {
        recyclerView.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
        if (tvEmpty != null) tvEmpty.setText("Loading events...");
    }

    // ── Teammate's original methods (unchanged) ───────────────────────────────

    public void showFragment(int destinationId, boolean showBottomNav) {
        showFragment(destinationId, showBottomNav, null);
    }

    public void showFragment(int destinationId, boolean showBottomNav, Bundle args) {
        clearBackStack();
        navHostFragment.setVisibility(View.VISIBLE);
        homeContent.setVisibility(View.GONE);
        appBar.setVisibility(View.GONE);
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        navController.navigate(destinationId, args);
    }

    public void showHome() {
        clearBackStack();
        navHostFragment.setVisibility(View.GONE);
        homeContent.setVisibility(View.VISIBLE);
        appBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
    }

    private void clearBackStack() {
        while (navController.getCurrentBackStackEntry() != null) {
            if (!navController.popBackStack()) break;
        }
    }

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

    public NavController getNavController() {
        return navController;
    }

    public void onGuestJoinAttempt() {
        showLoginPrompt("Sign in to join events.");
    }

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

    private void goToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}