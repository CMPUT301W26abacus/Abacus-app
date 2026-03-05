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
 * - Event list is hardcoded; replace with Firestore LiveData when Firebase is integrated.
 * - Role-based UI (admin delete buttons, organizer tools) not yet implemented.
 * - Bottom nav "Saved", "History", "Inbox" fragments are currently empty stubs.
 */
package com.example.abacus_app;

import android.app.DatePickerDialog;
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
import com.google.android.material.search.SearchBar;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private FragmentContainerView navHostFragment;
    private View homeContent;
    private AppBarLayout appBar;
    private BottomNavigationView bottomNav;

    // ── Filter state ───────────────────────────────────────────────────────────
    private String activeKeyword = "";
    private String activeDate    = ""; // "yyyy-MM-dd" or empty if not set
    private RecyclerView recyclerView;
    private LinearLayout layoutEmptyState;

    // Hardcoded test data — replace with Firestore data later
    // Format: "title|interests|date(yyyy-MM-dd)"
    private final List<String[]> allEvents = Arrays.asList(
            new String[]{"Summer Music Festival",  "music,outdoor,festival",  "2025-08-10"},
            new String[]{"Art Gallery Opening",    "art,culture,indoor",      "2025-07-22"},
            new String[]{"Community Food Drive",   "community,volunteer",     "2025-06-15"},
            new String[]{"Tech Meetup 2025",       "tech,networking,indoor",  "2025-09-05"},
            new String[]{"Charity Run",            "fitness,outdoor,charity", "2025-07-04"},
            new String[]{"Open Mic Night",         "music,comedy,indoor",     "2025-06-28"}
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // ── Teammate's original code (unchanged) ──────────────────────────────

        // Navigation bar colour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(80, 255, 255, 255));
        } else {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(180, 255, 255, 255));
        }

        // Views
        homeContent     = findViewById(R.id.home_content);
        navHostFragment = findViewById(R.id.nav_host_fragment);
        appBar          = findViewById(R.id.app_bar);
        bottomNav       = findViewById(R.id.bottom_nav);

        // Set up NavController
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        // Search bar text colour fix
        SearchBar searchBar = findViewById(R.id.search_bar);
        setSearchBarTextColor(searchBar);

        // Profile button → profile fragment
        ImageButton btnProfile = findViewById(R.id.btn_profile);
        btnProfile.setOnClickListener(v -> showFragment(R.id.mainProfileFragment, false));

        // QR scan button → QR scan fragment
        ImageButton btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> showFragment(R.id.mainQrScanFragment, false));

        // List item click → event details (hide bottom nav)
        recyclerView = findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        layoutEmptyState = findViewById(R.id.layout_empty_state);

        // Show full list on startup
        applyFilters();

        // Bottom nav
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

        // Handle back gesture and back button
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

        // ── Filter button → bottom sheet (US 01.01.04) ────────────────────────
        ImageButton btnFilter = findViewById(R.id.btn_filter);
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    // ── Filter logic ──────────────────────────────────────────────────────────

    /**
     * Opens the filter bottom sheet. Pre-fills any currently active filters
     * so the user can see and edit what's applied.
     */
    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.fragment_filter_bottom_sheet);

        TextInputEditText etKeyword = dialog.findViewById(R.id.et_keyword);
        Button btnPickDate     = dialog.findViewById(R.id.btn_pick_date);
        Button            btnApply        = dialog.findViewById(R.id.btn_apply_filters);
        Button            btnClear        = dialog.findViewById(R.id.btn_clear_filters);

        // Pre-fill with active filters
        if (etKeyword != null && !activeKeyword.isEmpty()) {
            etKeyword.setText(activeKeyword);
        }

        // Holds the date the user picked inside this dialog session
        final String[] pickedDate = {activeDate};

        // Update button label if a date is already active
        if (btnPickDate != null && !activeDate.isEmpty()) {
            btnPickDate.setText(activeDate);
        }

        // Date picker
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

        // Apply
        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                activeKeyword = etKeyword != null && etKeyword.getText() != null
                        ? etKeyword.getText().toString().trim().toLowerCase()
                        : "";
                activeDate = pickedDate[0];
                applyFilters();
                dialog.dismiss();
            });
        }

        // Clear — resets all filters and refreshes the full list
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
     * Filters allEvents using the active keyword and/or date and updates
     * the RecyclerView. Shows the empty state if nothing matches.
     *
     * Keyword matches against title and interests (case-insensitive).
     * Date matches events whose date equals the selected date exactly.
     * Both filters must match when both are active (AND logic).
     */
    private void applyFilters() {
        List<String> filtered = new ArrayList<>();

        for (String[] event : allEvents) {
            String title     = event[0].toLowerCase();
            String interests = event[1].toLowerCase();
            String date      = event[2];

            // Keyword filter — matches title or interests
            boolean keywordMatch = activeKeyword.isEmpty()
                    || title.contains(activeKeyword)
                    || interests.contains(activeKeyword);

            // Date filter — matches exact date
            boolean dateMatch = activeDate.isEmpty()
                    || date.equals(activeDate);

            if (keywordMatch && dateMatch) {
                filtered.add(event[0]); // add title for display
            }
        }

        // Update RecyclerView
        recyclerView.setAdapter(new EventAdapter(filtered,
                eventTitle -> showFragment(R.id.eventDetailsFragment, false)));

        // Show empty state if no results
        if (filtered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            // Update empty state message depending on whether filters are active
            TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
            if (tvEmpty != null) {
                boolean hasFilters = !activeKeyword.isEmpty() || !activeDate.isEmpty();
                tvEmpty.setText(hasFilters
                        ? "No matching events found"
                        : "No events available");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
        }
    }

    // ── Teammate's original methods (unchanged) ───────────────────────────────

    private void showFragment(int destinationId, boolean showBottomNav) {
        clearBackStack();
        navHostFragment.setVisibility(View.VISIBLE);
        homeContent.setVisibility(View.GONE);
        appBar.setVisibility(View.GONE);
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        navController.navigate(destinationId);
    }

    public void showHome() {
        clearBackStack();
        navHostFragment.setVisibility(View.GONE);
        homeContent.setVisibility(View.VISIBLE);
        appBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
    }

    private void clearBackStack() {
        NavController nav = navController;
        while (nav.getCurrentBackStackEntry() != null) {
            if (!nav.popBackStack()) break;
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
}