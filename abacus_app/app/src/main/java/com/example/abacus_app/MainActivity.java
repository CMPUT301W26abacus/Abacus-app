/**
 * MainActivity.java
 *
 * Role: The single Activity that hosts all fragments via the Navigation
 * component. Responsible for loading and displaying the event browse list,
 * applying keyword and date filters, managing bottom navigation, and
 * routing to event detail, profile, QR scan, and organizer/admin screens.
 */
package com.example.abacus_app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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

    private String resolvedUserKey = null;
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

        resolveUserKey();

        userRepository.getProfile(user -> {
            if (user != null) {
                userRole = (user.getRole() != null && !user.getRole().isEmpty())
                        ? user.getRole() : "entrant";
                runOnUiThread(this::setupBottomNav);
            } else {
                runOnUiThread(this::setupBottomNav);
            }
        });

        eventRepository = new EventRepository();
        loadEventsFromFirestore();
    }

    private void resolveUserKey() {
        if (isGuest) {
            String guestEmail = getSharedPreferences(
                    GuestSignUpFragment.PREFS_GUEST, Context.MODE_PRIVATE)
                    .getString(GuestSignUpFragment.PREF_GUEST_EMAIL, null);
            if (guestEmail != null) {
                resolvedUserKey = GuestSignUpFragment.emailToKey(guestEmail);
            }
            if (!allEvents.isEmpty()) applyFilters();
        } else {
            UserLocalDataSource local   = new UserLocalDataSource(getApplicationContext());
            UserRemoteDataSource remote = new UserRemoteDataSource(FirebaseFirestore.getInstance());
            new UserRepository(local, remote).getCurrentUserId(uuid -> {
                resolvedUserKey = uuid;
                if (!allEvents.isEmpty()) runOnUiThread(this::applyFilters);
            });
        }
    }

    private void setupBottomNav() {
        bottomNav.getMenu().clear();

        if ("organizer".equals(userRole) || "admin".equals(userRole)) {
            bottomNav.inflateMenu(R.menu.menu_bottom_nav_organizer);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showHome();
                    return true;
                } else if (id == R.id.nav_create) {
                    showFragment(R.id.organizerCreateFragment, true);
                    return true;
                } else if (id == R.id.nav_manage) {
                    showFragment(R.id.organizerManageFragment, true);
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
            bottomNav.inflateMenu(R.menu.menu_bottom_nav);
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

    private void loadEventsFromFirestore() {
        showLoadingState();

        eventsListener = FirebaseFirestore.getInstance()
                .collection("events")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        applyFilters();
                        return;
                    }
                    if (querySnapshot == null) return;

                    allEvents.clear();
                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : querySnapshot.getDocuments()) {
                        try {
                            Event event = doc.toObject(Event.class);
                            if (event != null) {
                                if (event.getEventId() == null || event.getEventId().isEmpty()) {
                                    event.setEventId(doc.getId());
                                }
                                allEvents.add(event);
                            }
                        } catch (Exception ex) {
                            android.util.Log.w("MainActivity", "Skipping bad doc: " + doc.getId(), ex);
                        }
                    }
                    applyFilters();
                });
    }

    private void applyFilters() {
        List<Event> filtered = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date now = new Date();

        for (Event event : allEvents) {
            String title       = event.getTitle()       != null ? event.getTitle().toLowerCase()       : "";
            String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";

            boolean keywordMatch = activeKeyword.isEmpty()
                    || title.contains(activeKeyword)
                    || description.contains(activeKeyword);

            boolean dateMatch = true;
            if (!activeDate.isEmpty() && event.getRegistrationStart() != null) {
                String eventDate = sdf.format(event.getRegistrationStart().toDate());
                dateMatch = eventDate.equals(activeDate);
            }

            boolean notExpired = true;
            if (event.getRegistrationEnd() != null) {
                notExpired = event.getRegistrationEnd().toDate().after(now);
            }
            
            // US: Private events should not be visible in public browse list
            boolean isPublic = !event.isPrivate();

            if (keywordMatch && dateMatch && notExpired && isPublic) filtered.add(event);
        }

        boolean isAdminUser = "admin".equals(userRole);

        recyclerView.setAdapter(new EventAdapter(
                filtered,
                (eventTitle, autoJoin) -> {
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
                    args.putBoolean(EventDetailsFragment.ARG_AUTO_JOIN, autoJoin);
                    showFragment(R.id.eventDetailsFragment, false, args);
                },
                isAdminUser ? this::confirmDeleteEvent : null,
                isAdminUser,
                resolvedUserKey,
                isGuest
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

    @Override
    protected void onResume() {
        super.onResume();
        if (isGuest && resolvedUserKey == null) {
            String guestEmail = getSharedPreferences(
                    GuestSignUpFragment.PREFS_GUEST, Context.MODE_PRIVATE)
                    .getString(GuestSignUpFragment.PREF_GUEST_EMAIL, null);
            if (guestEmail != null) {
                resolvedUserKey = GuestSignUpFragment.emailToKey(guestEmail);
            }
        }
        if (!allEvents.isEmpty()) applyFilters();
    }

    private void confirmDeleteEvent(Event event) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Event")
                .setMessage("Remove \"" + event.getTitle() + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> softDeleteEvent(event))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void softDeleteEvent(Event event) {
        if (event.getEventId() == null || event.getEventId().isEmpty()) return;
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

    private void showLoadingState() {
        recyclerView.setVisibility(View.GONE);
        layoutEmptyState.setVisibility(View.VISIBLE);
        TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
        if (tvEmpty != null) tvEmpty.setText("Loading events...");
    }

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (eventsListener != null) eventsListener.remove();
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

    public NavController getNavController() { return navController; }

    public String getUserRole() { return userRole; }

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