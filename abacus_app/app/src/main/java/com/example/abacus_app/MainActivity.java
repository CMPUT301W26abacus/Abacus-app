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

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
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
import com.google.android.material.search.SearchBar;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private FragmentContainerView navHostFragment;
    private View homeContent;
    private AppBarLayout appBar;
    private BottomNavigationView bottomNav;

    private UserRepository userRepository; //UUID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 1. Build UserLocalDataSource using Kotlin extension
        UserLocalDataSource localDataSource = new UserLocalDataSource(
                DataStoreHelperKt.getDataStore(getApplicationContext())
        );

        // 2. Build UserRepository
        userRepository = new UserRepository(
                localDataSource,
                FirebaseFirestore.getInstance()
        );

        // 3. Initialize user on app launch
        userRepository.initializeUserAsync();

        // Navigation bar colour
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(80, 255, 255, 255));
        } else {
            getWindow().setNavigationBarColor(android.graphics.Color.argb(180, 255, 255, 255));
        }

        // Views
        homeContent = findViewById(R.id.home_content);
        navHostFragment = findViewById(R.id.nav_host_fragment);
        appBar = findViewById(R.id.app_bar);
        bottomNav = findViewById(R.id.bottom_nav);

        // Set up NavController
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        // Search bar text colour fix
        SearchBar searchBar = findViewById(R.id.search_bar);
        setSearchBarTextColor(searchBar);

        // Profile button → profile fragment
        ImageButton btnProfile = findViewById(R.id.btn_profile);
        btnProfile.setOnClickListener(v -> showFragment(R.id.mainProfileFragment, true));

        // QR scan button → QR scan fragment
        ImageButton btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> showFragment(R.id.mainQrScanFragment, true));

        // Hardcoded test data — replace with Firestore data later
        List<String> testEvents = Arrays.asList(
                "Summer Music Festival",
                "Art Gallery Opening",
                "Community Food Drive",
                "Tech Meetup 2025",
                "Charity Run",
                "Open Mic Night",
                "Summer Music Festival",
                "Art Gallery Opening",
                "Community Food Drive",
                "Tech Meetup 2025",
                "Charity Run",
                "Open Mic Night"
        );

        // List item click → event details (hide bottom nav)
        RecyclerView recyclerView = findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(new EventAdapter(testEvents, eventTitle ->
                showFragment(R.id.eventDetailsFragment, false)));

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


    }

    /**
     * Shows a destination fragment by making the NavHostFragment overlay visible
     * and hiding the home UI and app bar. Clears the back stack first so previous
     * tab fragments don't remain underneath.
     *
     * @param destinationId The nav graph resource ID of the fragment to navigate to.
     * @param showBottomNav Whether to keep the bottom nav visible on this screen.
     */
    private void showFragment(int destinationId, boolean showBottomNav) {
        // Clear the entire back stack before navigating so stale fragments
        // from previous tabs don't interfere with the back button
        clearBackStack();

        navHostFragment.setVisibility(View.VISIBLE);
        homeContent.setVisibility(View.GONE);
        appBar.setVisibility(View.GONE);
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        navController.navigate(destinationId);
    }

    /**
     * Returns to the home screen by hiding the fragment overlay and
     * restoring the home UI, app bar, and bottom nav. Clears the fragment back stack.
     */
    public void showHome() {
        clearBackStack();
        navHostFragment.setVisibility(View.GONE);
        homeContent.setVisibility(View.VISIBLE);
        appBar.setVisibility(View.VISIBLE);
        bottomNav.setVisibility(View.VISIBLE);
    }

    /**
     * Clears the entire NavController back stack so no stale fragments
     * remain when switching between tabs or returning to home.
     */
    private void clearBackStack() {
        NavController nav = navController;
        while (nav.getCurrentBackStackEntry() != null) {
            if (!nav.popBackStack()) break;
        }
    }

    /**
     * Recursively walks the view hierarchy of a ViewGroup and applies
     * the app text colour to any TextView found. Used to fix Material 3
     * SearchBar text colour which cannot be set reliably via XML.
     *
     * @param viewGroup The root ViewGroup to walk — pass the SearchBar instance.
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
     * Exposes the NavController to fragments that need to trigger navigation
     * from outside the normal NavHost flow.
     *
     * @return The NavController managing the fragment back stack.
     */
    public NavController getNavController() {
        return navController;
    }
}