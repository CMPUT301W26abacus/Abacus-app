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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

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

    private String userRole = "entrant";

    private String activeKeyword = "";
    private String activeDate    = "";
    private RecyclerView recyclerView;
    private LinearLayout layoutEmptyState;

    private final List<String[]> allEvents = Arrays.asList(
            new String[]{"Summer Music Festival",  "music,outdoor,festival",  "2025-08-10"},
            new String[]{"Art Gallery Opening",    "art,culture,indoor",      "2025-07-22"},
            new String[]{"Community Food Drive",   "community,volunteer",     "2025-06-15"},
            new String[]{"Tech Meetup 2025",       "tech,networking,indoor",  "2025-09-05"},
            new String[]{"Charity Run",            "fitness,outdoor,charity", "2025-07-04"},
            new String[]{"Open Mic Night",         "music,comedy,indoor",     "2025-06-28"}
    );

    private UserRepository userRepository;
    private boolean isGuest;

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

        recyclerView = findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        applyFilters();

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
    }

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
                    // Admin sees image/profile moderation; organizer sees activity logs
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

    private void applyFilters() {
        List<String> filtered = new ArrayList<>();

        for (String[] event : allEvents) {
            String title     = event[0].toLowerCase();
            String interests = event[1].toLowerCase();
            String date      = event[2];

            boolean keywordMatch = activeKeyword.isEmpty()
                    || title.contains(activeKeyword)
                    || interests.contains(activeKeyword);
            boolean dateMatch = activeDate.isEmpty() || date.equals(activeDate);

            if (keywordMatch && dateMatch) filtered.add(event[0]);
        }

        recyclerView.setAdapter(new EventAdapter(filtered,
                eventTitle -> showFragment(R.id.eventDetailsFragment, false)));

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

    public void showFragment(int destinationId, boolean showBottomNav) {
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