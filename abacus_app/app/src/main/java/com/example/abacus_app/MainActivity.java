/**
 * MainActivity - Main screen hosting all app features
 *
 * The central hub of the app. Hosts all fragments and manages:
 * - Displaying the event list with search and date filtering
 * - Bottom navigation menu (changes based on user role: entrant/organizer)
 * - Navigation to event details, profile, QR scan, and organizer tools
 * - User role and guest status management
 *
 * Key Features:
 * - Dynamically rebuilds bottom nav when user role changes (e.g., after logout)
 * - Instantly switches between light/dark themes when system setting changes
 * - Maintains event list and filter state
 * - Handles guest vs logged-in user permissions
 *
 * User Role Updates:
 * - onUserLoggedOut(): Called when user signs out, resets role to "entrant"
 *   and rebuilds bottom navigation immediately
 * - setEffectiveRole(): Updates user role and refreshes navigation
 *
 * Theme Switching:
 * - onResume() detects dark mode changes and calls recreate() to reload
 *   with correct theme colors
 * - Font scale applied after setContentView() to avoid freezing configuration
 *
 */
package com.example.abacus_app;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MenuItem;
import android.view.MotionEvent;
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
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentContainerView;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
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
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout homeContent;
    private AppBarLayout appBar;
    private BottomNavigationView bottomNav;

    private String userRole      = "entrant";
    private String bottomNavRole = null; // tracks which role the bottom nav was last built for

    // ── Filter state ──────────────────────────────────────────────────────────
    private String activeKeyword    = "";    // search bar — applied on every keystroke
    private String activeFilterTags = "";    // filter sheet chip selections — comma-separated, applied on Apply
    private String activeDate       = "";    // filter sheet date picker — matches against eventStart/eventEnd
    private boolean filterOpenOnly  = false; // filter sheet "Open for registration only"
    private boolean filterHasSpots  = false; // filter sheet "Has spots left"

    private RecyclerView recyclerView;
    private LinearLayout layoutEmptyState;

    private final List<Event> allEvents = new ArrayList<>();

    private RecyclerView carouselRecyclerView;
    private CarouselEventAdapter carouselAdapter;
    private TextView tvFeaturedLabel;
    private TextView tvForYouLabel;
    private View layoutPreferencesFooter;
    private TextView tvPreferencesFooter;

    private java.util.Map<String, Object> userPreferences = null;

    private EventRepository eventRepository;
    private UserRepository userRepository;
    private boolean isGuest;

    /**
     * Resolved user identifier passed into EventAdapter for join-status checks.
     * For authenticated users: their Firebase UUID.
     * For guests: their sanitised email key (GuestSignUpFragment.emailToKey).
     * Null until resolved asynchronously.
     */
    private String resolvedUserKey = null;

    private ListenerRegistration eventsListener;

    /** App-level role model supports only entrant/organizer for signed-in users. */
    private String normalizeAppRole(String role) {
        if ("admin".equals(role)) return "admin";
        if ("organizer".equals(role)) return "organizer";
        return "entrant";
    }

    // ── One-handed mode ───────────────────────────────────────────────────────
    private boolean  isOneHandedActive = false;
    private Animator chevronAnimator   = null;
    // Raw touch tracking for one-handed swipe (emulator-friendly, no GestureDetector)
    private float ohTouchStartX = 0f, ohTouchStartY = -1f;
    private long  ohTouchStartMs = 0;

    // ── Swipe back / forward navigation ──────────────────────────────────────
    private GestureDetectorCompat navSwipeDetector;

    @Override
    protected void attachBaseContext(android.content.Context newBase) {
        AccessibilityHelper helper = new AccessibilityHelper(newBase);
        android.content.res.Configuration config =
                AccessibilityHelper.buildConfig(newBase, helper.getTextScale());
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UserLocalDataSource localDataSource = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);
        userRepository.initializeUserAsync();

        isGuest = getIntent().getBooleanExtra("isGuest", false);
        String intentRole = getIntent().getStringExtra("userRole");
        if (intentRole == null || intentRole.isEmpty()) {
            intentRole = getIntent().getStringExtra("role");
        }
        if (intentRole != null && !intentRole.isEmpty()) userRole = normalizeAppRole(intentRole);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        android.util.Log.d("MainActivity", "isGuest=" + isGuest
                + " authenticated=" + (currentUser != null));

        if (!isGuest && currentUser == null) {
            goToSplash();
            return;
        }

        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean isNightMode = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        windowInsetsController.setAppearanceLightNavigationBars(!isNightMode);
        windowInsetsController.setAppearanceLightStatusBars(!isNightMode);

        homeContent     = findViewById(R.id.home_content);
        navHostFragment = findViewById(R.id.nav_host_fragment);
        appBar          = findViewById(R.id.app_bar);
        bottomNav       = findViewById(R.id.bottom_nav);

        homeContent.setColorSchemeResources(R.color.black);
        homeContent.setOnRefreshListener(() -> {
            allEvents.clear();
            if (eventsListener != null) eventsListener.remove();
            loadEventsFromFirestore();
            userRepository.getProfile(user -> {
                if (user != null) userPreferences = user.getPreferences();
                runOnUiThread(() -> homeContent.setRefreshing(false));
            });
        });

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        navController = navHost.getNavController();

        // If we recreated the activity to apply text scale, go back to accessibility settings
        AccessibilityHelper a11yHelper = new AccessibilityHelper(this);
        if (a11yHelper.shouldReturnToAccessibility()) {
            a11yHelper.clearReturnToAccessibility();
            navController.navigate(R.id.mainProfileFragment);
            navController.navigate(R.id.accessibilityFragment);
        }

        // ── Search bar real-time filtering ────────────────────────────────────
        TextInputEditText searchBar = findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                activeKeyword = s.toString().trim().toLowerCase();
                applyFilters();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Clear focus when search action is pressed
        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                searchBar.clearFocus();
                return true;
            }
            return false;
        });

        ImageButton btnProfile = findViewById(R.id.btn_profile);
        btnProfile.setOnClickListener(v -> showFragment(R.id.mainProfileFragment, false));

        ImageButton btnScan = findViewById(R.id.btn_scan);
        btnScan.setOnClickListener(v -> showFragment(R.id.mainQrScanFragment, false));

        recyclerView     = findViewById(R.id.rv_events);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        layoutPreferencesFooter = findViewById(R.id.layoutPreferencesFooter);
        tvPreferencesFooter     = findViewById(R.id.tvPreferencesFooter);
        TextView tvChangePreferences = findViewById(R.id.tvChangePreferences);
        tvChangePreferences.setOnClickListener(v ->
                showFragment(R.id.preferencesFragment, false));

        // Carousel setup
        carouselRecyclerView = findViewById(R.id.rv_carousel);
        tvFeaturedLabel      = findViewById(R.id.tv_featured_label);
        tvForYouLabel        = findViewById(R.id.tv_for_you_label);
        carouselRecyclerView.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        carouselAdapter = new CarouselEventAdapter(new ArrayList<>(), event -> {
            Bundle args = new Bundle();
            args.putString(EventDetailsFragment.ARG_EVENT_TITLE,
                    event.getTitle() != null ? event.getTitle() : "");
            args.putString(EventDetailsFragment.ARG_EVENT_ID,
                    event.getEventId() != null ? event.getEventId() : "");
            args.putBoolean(EventDetailsFragment.ARG_AUTO_JOIN, false);
            showFragment(R.id.eventDetailsFragment, false, args);
        });
        carouselRecyclerView.setAdapter(carouselAdapter);

        setupBottomNav(); // build nav immediately using the role from the Intent

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

        // ── Resolve user key for join-status checks ────────────────────────────
        resolveUserKey();

        // ── Wait for profile to load to set up navigation ──────────────────────
        userRepository.getProfile(user -> {
            if (user != null) {
                // Only update the role from Firestore for authenticated (non-guest) users.
                // Guest sessions must stay as "entrant" regardless of what the Firestore
                // doc contains (e.g. an old admin account stored at the same ANDROID_ID).
                if (!isGuest) {
                    userRole = normalizeAppRole(user.getRole());
                }
                userPreferences = user.getPreferences();
                android.util.Log.d("MainActivity", "User role resolved: " + userRole);
                runOnUiThread(() -> {
                    setupBottomNav();
                    if (!allEvents.isEmpty()) applyFilters();
                });
            } else {
                runOnUiThread(this::setupBottomNav);
            }
        });

        eventRepository = new EventRepository();
        loadEventsFromFirestore();
        setupOneHandedMode();
        setupNavSwipeGesture();
    }

    // ── User key resolution ───────────────────────────────────────────────────

    /**
     * Resolves the key used to look up registrations in Firestore.
     *
     * Authenticated users: resolves via UserRepository (UUID).
     * Guests: reads the stored email from SharedPreferences and sanitises it
     *         via GuestSignUpFragment.emailToKey — matching the doc ID format
     *         written by GuestSignUpFragment.
     *
     * Once resolved, re-applies filters so the adapter is rebuilt with the key.
     */
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
            FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
            String email = authUser != null ? authUser.getEmail() : null;
            UserLocalDataSource local   = new UserLocalDataSource(getApplicationContext());
            UserRemoteDataSource remote = new UserRemoteDataSource(FirebaseFirestore.getInstance());
            new UserRepository(local, remote).getCurrentUserId(uuid -> {
                if (email != null && !email.trim().isEmpty() && uuid == null) {
                    resolvedUserKey = GuestSignUpFragment.emailToKey(email.trim().toLowerCase(Locale.US));
                    if (!allEvents.isEmpty()) runOnUiThread(this::applyFilters);
                } else {
                    resolvedUserKey = uuid;
                    if (!allEvents.isEmpty()) runOnUiThread(this::applyFilters);
                }
            });
        }
    }

    private void setupBottomNav() {
        String roleGroup = ("organizer".equals(userRole) || "admin".equals(userRole))
                ? "organizer" : "entrant";
        if (roleGroup.equals(bottomNavRole)) return;
        bottomNavRole = roleGroup;
        bottomNav.setOnItemSelectedListener(null);
        bottomNav.getMenu().clear();

        if ("organizer".equals(userRole) || "admin".equals(userRole)) {
            bottomNav.inflateMenu(R.menu.menu_bottom_nav_organizer);
            bottomNav.setOnItemSelectedListener(item -> {
                int id = item.getItemId();
                if (id == R.id.nav_home) {
                    showHome();
                    return true;
                } else if (id == R.id.nav_tools) {
                    showFragment(R.id.organizerCreateFragment, true);
                    return true;
                } else if (id == R.id.nav_logs) {
                    if ("admin".equals(userRole)) {
                        showFragment(R.id.adminLogsFragment, true);
                    } else {
                        showFragment(R.id.organizerManageFragment, true);
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
                    if ("entrant".equals(userRole)) showFragment(R.id.nav_saved, true);
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

                    for (com.google.firebase.firestore.DocumentSnapshot doc
                            : querySnapshot.getDocuments()) {
                        try {
                            Event event = doc.toObject(Event.class);
                            if (event != null) {
                                if (!Boolean.TRUE.equals(event.getIsDeleted())) {
                                    if (event.getEventId() == null || event.getEventId().isEmpty()) {
                                        event.setEventId(doc.getId());
                                    }
                                    allEvents.add(event);
                                }
                            }
                        } catch (Exception ex) {
                            android.util.Log.w("MainActivity",
                                    "Skipping bad doc: " + doc.getId(), ex);
                        }
                    }
                    applyFilters();
                });
    }

    // ── Filter & Adapter ──────────────────────────────────────────────────────

    /**
     * Filters allEvents by the active keyword, tags, date, availability, and expiry,
     * then binds the result to the RecyclerView via EventAdapter.
     *
     * - activeKeyword:    search bar, matches title + description, applied on keystroke
     * - activeFilterTags: comma-separated chip selections, matches event.tags then text
     * - activeDate:       checked against eventStart/eventEnd (the actual event dates,
     *                     not the registration window — more intuitive for entrants)
     * - filterOpenOnly:   excludes events whose registration window has not yet started
     * - filterHasSpots:   excludes events where waitlistCount >= waitlistCapacity
     */
    private void applyFilters() {
        List<Event> filtered = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date now = new Date();

        String[] selectedCategories = activeFilterTags.isEmpty()
                ? new String[0]
                : activeFilterTags.split(",");

        for (Event event : allEvents) {

            String title = event.getTitle() != null ? event.getTitle().toLowerCase() : "";
            String description = event.getDescription() != null ? event.getDescription().toLowerCase() : "";

            // ── Search bar match (title + description) ────────────────────────
            boolean searchMatch = activeKeyword.isEmpty()
                    || title.contains(activeKeyword)
                    || description.contains(activeKeyword);

            // ── Filter sheet: category chip match ─────────────────────────────
            boolean tagMatch = true;
            if (selectedCategories.length > 0) {
                tagMatch = false;
                List<String> eventTags = event.getTags();
                for (String category : selectedCategories) {
                    String cat = category.trim().toLowerCase();
                    boolean hitTag = eventTags != null && !eventTags.isEmpty()
                            && eventTags.stream().anyMatch(t -> t.toLowerCase().contains(cat));
                    boolean hitText = title.contains(cat) || description.contains(cat);
                    if (hitTag || hitText) {
                        tagMatch = true;
                        break;
                    }
                }
            }

            // ── Filter sheet: date — checked against eventStart/eventEnd ──────
            // Entrants filter by when the event happens, not when registration opens.
            // Falls back to registration dates for older events that have no eventStart set.
            boolean dateMatch = true;
            if (!activeDate.isEmpty()) {
                try {
                    Date picked = sdf.parse(activeDate);
                    if (picked != null) {
                        if (event.getEventStart() != null) {
                            // Use event dates when available
                            Date start = event.getEventStart().toDate();
                            Date end = event.getEventEnd() != null
                                    ? event.getEventEnd().toDate() : start;
                            dateMatch = !picked.before(start) && !picked.after(end);
                        } else if (event.getRegistrationStart() != null) {
                            // Fallback for events created before event dates were added
                            Date start = event.getRegistrationStart().toDate();
                            Date end = event.getRegistrationEnd() != null
                                    ? event.getRegistrationEnd().toDate() : start;
                            dateMatch = !picked.before(start) && !picked.after(end);
                        }
                    }
                } catch (Exception e) {
                    dateMatch = true;
                }
            }

            // ── Filter sheet: open for registration only ──────────────────────
            boolean openMatch = true;
            if (filterOpenOnly) {
                openMatch = event.getRegistrationStart() != null
                        && !now.before(event.getRegistrationStart().toDate());
            }

            // ── Filter sheet: has spots left ──────────────────────────────────
            boolean spotsMatch = true;
            if (filterHasSpots) {
                Integer capacity = event.getWaitlistCapacity();
                Integer count = event.getWaitlistCount();
                if (capacity != null) {
                    spotsMatch = count == null || count < capacity;
                }
            }

            // ── Expiry check (unchanged) ──────────────────────────────────────
            boolean notExpired = true;
            if (event.getRegistrationEnd() != null) {
                notExpired = event.getRegistrationEnd().toDate().after(now);
            }

            // ── Visibility: private events only shown to their organizer ──────
            boolean isPublic = !event.isPrivate();
            boolean isCoOrganizer = resolvedUserKey != null && event.getCoOrganizers() != null && event.getCoOrganizers().contains(resolvedUserKey);
            boolean isOrganizerOfPrivate = false;
            boolean isAdminViewingPrivate = "admin".equals(userRole) && !isPublic;

            if (!isPublic && !isAdminViewingPrivate) {
                String organizerId = event.getOrganizerId();
                boolean byUUID = resolvedUserKey != null && resolvedUserKey.equals(organizerId);
                FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
                boolean byFirebase = fu != null && fu.getUid().equals(organizerId);
                isOrganizerOfPrivate = byUUID || byFirebase;
            }

            if (searchMatch && tagMatch && dateMatch && openMatch && spotsMatch && notExpired
                    && (isPublic || isOrganizerOfPrivate || isAdminViewingPrivate || isCoOrganizer)) {
                filtered.add(event);
            }
        }


        // ── Update filter button tint to show active state ────────────────────
        ImageButton btnFilter = findViewById(R.id.btn_filter);
        if (btnFilter != null) {
            boolean filtersActive = !activeFilterTags.isEmpty()
                    || !activeDate.isEmpty()
                    || filterOpenOnly
                    || filterHasSpots;
            btnFilter.setColorFilter(filtersActive
                    ? ContextCompat.getColor(this, R.color.orange)
                    : ContextCompat.getColor(this, R.color.grey));
        }

        // ── Update carousel with up to 5 soonest events ───────────────────────
        List<Event> featuredEvents = new ArrayList<>(filtered);
        featuredEvents.sort((a, b) -> {
            if (a.getRegistrationStart() == null) return 1;
            if (b.getRegistrationStart() == null) return -1;
            return a.getRegistrationStart().compareTo(b.getRegistrationStart());
        });
        if (featuredEvents.size() > 5) featuredEvents = featuredEvents.subList(0, 5);
        carouselAdapter.setEvents(featuredEvents);
        boolean showCarousel = !featuredEvents.isEmpty();
        carouselRecyclerView.setVisibility(showCarousel ? View.VISIBLE : View.GONE);
        tvFeaturedLabel.setVisibility(showCarousel ? View.VISIBLE : View.GONE);

        // ── Preference-based filter + sort ("For You") ────────────────────────
        boolean hasPreferences = hasActivePreferences(userPreferences);
        if (hasPreferences) {
            filtered = applyPreferenceFilter(filtered, userPreferences);
            tvForYouLabel.setVisibility(View.VISIBLE);
            tvForYouLabel.setText("For You");
        } else {
            tvForYouLabel.setVisibility(View.VISIBLE);
            tvForYouLabel.setText("All Events");
        }

        boolean isAdminUser = "admin".equals(userRole);
        boolean canManageEvents = !isGuest
                && ("organizer".equals(userRole) || "admin".equals(userRole));
        final List<Event> finalFiltered = filtered;

        recyclerView.setAdapter(new EventAdapter(
                finalFiltered,
                (eventTitle, autoJoin) -> {
                    String eventId = "";
                    for (Event e : finalFiltered) {
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
                event -> {
                    Bundle args = new Bundle();
                    args.putString("EVENT_ID", event.getEventId());
                    args.putString("EVENT_TITLE", event.getTitle());
                    showFragment(R.id.organizerManageFragment, false, args);
                },
                isAdminUser,
                canManageEvents,
                resolvedUserKey,
                isGuest
        ));

        if (finalFiltered.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            layoutEmptyState.setVisibility(View.VISIBLE);
            layoutPreferencesFooter.setVisibility(View.GONE);
            TextView tvEmpty = layoutEmptyState.findViewById(R.id.tv_empty_message);
            if (tvEmpty != null) {
                if (hasPreferences) {
                    tvEmpty.setText("No events match your preferences");
                } else {
                    boolean hasFilters = !activeKeyword.isEmpty()
                            || !activeFilterTags.isEmpty()
                            || !activeDate.isEmpty()
                            || filterOpenOnly
                            || filterHasSpots;
                    tvEmpty.setText(hasFilters ? "No matching events found" : "No events available");
                }
            }
            TextView tvEmptySub = layoutEmptyState.findViewById(R.id.tv_empty_sub);
            if (tvEmptySub != null) {
                tvEmptySub.setText(hasPreferences
                        ? "Try changing your preferences to see more events."
                        : "Check back later for upcoming events.");
            }
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            layoutEmptyState.setVisibility(View.GONE);
            layoutPreferencesFooter.setVisibility(hasPreferences ? View.VISIBLE : View.GONE);
            if (hasPreferences && tvPreferencesFooter != null) {
                tvPreferencesFooter.setText("Showing " + finalFiltered.size()
                        + " event" + (finalFiltered.size() == 1 ? "" : "s")
                        + " matching your preferences.");
            }
        }
    }

    /** True when the user has at least one category preference saved. */
    private boolean hasActivePreferences(java.util.Map<String, Object> prefs) {
        if (prefs == null || prefs.isEmpty()) return false;
        Object raw = prefs.get("categories");
        if (!(raw instanceof java.util.List)) return false;
        return !((java.util.List<?>) raw).isEmpty();
    }

    private List<Event> applyPreferenceFilter(List<Event> events,
                                              java.util.Map<String, Object> prefs) {
        Object categoriesRaw = prefs.get("categories");
        if (!(categoriesRaw instanceof java.util.List)) return events;
        @SuppressWarnings("unchecked")
        java.util.List<String> preferred = (java.util.List<String>) categoriesRaw;
        if (preferred.isEmpty()) return events;

        List<Event> matching = new ArrayList<>();
        for (Event e : events) {
            if (eventMatchesAny(e, preferred)) matching.add(e);
        }
        return matching;
    }

    private boolean eventMatchesAny(Event event, java.util.List<String> keywords) {
        String title = event.getTitle()       != null ? event.getTitle().toLowerCase()       : "";
        String desc  = event.getDescription() != null ? event.getDescription().toLowerCase() : "";
        for (String kw : keywords) {
            String lower = kw.toLowerCase();
            if (title.contains(lower) || desc.contains(lower)) return true;
        }
        return false;
    }

    // ── onResume ──────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();

        if (!isGuest) {
            userRepository.getProfile(user -> {
                if (user == null) {
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        }

        if (isOneHandedActive && !new AccessibilityHelper(this).isOneHandedMode()) {
            deactivateOneHanded();
        }
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

    // ── Admin event deletion ──────────────────────────────────────────────────

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
                        android.util.Log.e("MainActivity",
                                "Failed to delete event: " + e.getMessage()));
    }

    // ── Bottom sheet filter ───────────────────────────────────────────────────

    /**
     * Shows the filter bottom sheet.
     *
     * 1. Category chips  → activeFilterTags
     * 2. Date picker     → activeDate (matches against eventStart/eventEnd)
     * 3. Availability    → filterOpenOnly, filterHasSpots
     */
    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.fragment_filter_bottom_sheet);

        ChipGroup chipGroupFilter  = dialog.findViewById(R.id.chip_group_filter);
        Button btnPickDate         = dialog.findViewById(R.id.btn_pick_date);
        MaterialSwitch switchOpen  = dialog.findViewById(R.id.switch_open_only);
        MaterialSwitch switchSpots = dialog.findViewById(R.id.switch_has_spots);
        Button btnApply            = dialog.findViewById(R.id.btn_apply_filters);
        Button btnClear            = dialog.findViewById(R.id.btn_clear_filters);

        if (switchOpen  != null) switchOpen.setChecked(filterOpenOnly);
        if (switchSpots != null) switchSpots.setChecked(filterHasSpots);

        if (chipGroupFilter != null && !activeFilterTags.isEmpty()) {
            String[] active = activeFilterTags.split(",");
            for (int i = 0; i < chipGroupFilter.getChildCount(); i++) {
                Chip chip = (Chip) chipGroupFilter.getChildAt(i);
                for (String tag : active) {
                    if (chip.getText().toString().equalsIgnoreCase(tag.trim())) {
                        chip.setChecked(true);
                        break;
                    }
                }
            }
        }

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
                List<String> selected = new ArrayList<>();
                if (chipGroupFilter != null) {
                    for (int i = 0; i < chipGroupFilter.getChildCount(); i++) {
                        Chip chip = (Chip) chipGroupFilter.getChildAt(i);
                        if (chip.isChecked()) selected.add(chip.getText().toString());
                    }
                }
                activeFilterTags = String.join(",", selected);
                activeDate       = pickedDate[0];
                filterOpenOnly   = switchOpen  != null && switchOpen.isChecked();
                filterHasSpots   = switchSpots != null && switchSpots.isChecked();
                applyFilters();
                dialog.dismiss();
            });
        }

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                activeFilterTags = "";
                activeDate       = "";
                filterOpenOnly   = false;
                filterHasSpots   = false;
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

    // ── Navigation ────────────────────────────────────────────────────────────

    public static final NavOptions NAV_OPTS_FORWARD = new NavOptions.Builder()
            .setEnterAnim(R.anim.nav_slide_in_right)
            .setExitAnim(R.anim.nav_slide_out_left)
            .setPopEnterAnim(R.anim.nav_slide_in_left)
            .setPopExitAnim(R.anim.nav_slide_out_right)
            .build();

    private static final NavOptions NAV_OPTS_TAB = new NavOptions.Builder()
            .setEnterAnim(0)
            .setExitAnim(0)
            .setPopEnterAnim(0)
            .setPopExitAnim(0)
            .build();

    public void showFragment(int destinationId, boolean showBottomNav) {
        showFragment(destinationId, showBottomNav, null);
    }

    /**
     * Navigates to a destination inside the {@link androidx.navigation.NavController} and
     * manages the visibility of the home screen vs. the nav host.
     *
     * <p>When {@code showBottomNav} is {@code true} (top-level tab switch), the back stack is
     * cleared first so that pressing Back from any tab returns the user to the home screen.
     * When transitioning from the home screen to any fragment, the back stack is also cleared
     * so that Back skips the nav-graph start destination and goes straight to home.
     *
     * @param destinationId Nav-graph resource ID of the destination fragment.
     * @param showBottomNav {@code true} for top-level tab navigations (bottom nav visible);
     *                      {@code false} for sub-page navigations (bottom nav hidden).
     * @param args          Optional arguments {@link Bundle} passed to the destination, or null.
     */
    public void showFragment(int destinationId, boolean showBottomNav, Bundle args) {
        // Clear back stack for top-level tabs to ensure clean navigation between tabs
        if (showBottomNav) clearBackStack();
        bottomNav.setVisibility(showBottomNav ? View.VISIBLE : View.GONE);
        if (navHostFragment.getVisibility() != View.VISIBLE) {
            // Transitioning from home to NavHost — clear back stack so pressing back
            // naturally returns to home without landing on start destination
            clearBackStack();
            navHostFragment.setAlpha(0f);
            navHostFragment.setVisibility(View.VISIBLE);
            navHostFragment.animate().alpha(1f).setDuration(180).start();
            homeContent.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> {
                        homeContent.setVisibility(View.GONE);
                        homeContent.setAlpha(1f);
                        appBar.setVisibility(View.GONE);
                    }).start();
        } else {
            homeContent.setVisibility(View.GONE);
            appBar.setVisibility(View.GONE);
        }
        NavOptions opts = showBottomNav ? NAV_OPTS_TAB : NAV_OPTS_FORWARD;
        navController.navigate(destinationId, args, opts);
    }

    /**
     * Returns the UI to the home screen (the scrollable event carousel).
     *
     * <p>Clears the NavController back stack, hides the nav host fragment, and cross-fades
     * the home content in. The Home item in the bottom navigation bar is checked without
     * triggering the item-selected listener (which would otherwise cause infinite recursion).
     */
    public void showHome() {
        clearBackStack();
        bottomNav.setVisibility(View.VISIBLE);
        // Update the visual highlight without firing the item-selected listener
        // (setSelectedItemId would trigger showHome() recursively → StackOverflowError)
        MenuItem homeItem = bottomNav.getMenu().findItem(R.id.nav_home);
        if (homeItem != null) homeItem.setChecked(true);
        if (homeContent.getVisibility() != View.VISIBLE) {
            homeContent.setAlpha(0f);
            homeContent.setVisibility(View.VISIBLE);
            appBar.setVisibility(View.VISIBLE);
            homeContent.animate().alpha(1f).setDuration(180).start();
            navHostFragment.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> {
                        navHostFragment.setVisibility(View.GONE);
                        navHostFragment.setAlpha(1f);
                    }).start();
        } else {
            navHostFragment.setVisibility(View.GONE);
            appBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Removes all entries from the NavController back stack, including the start destination.
     *
     * <p>Attempts {@code popBackStack(startId, inclusive=true)} first as an O(1) fast path.
     * If the start destination is no longer on the stack (e.g. it was already popped by a
     * previous call), the stack is drained manually one entry at a time to avoid leaving
     * stale fragments.
     */
    private void clearBackStack() {
        int startId = navController.getGraph().getStartDestinationId();
        if (navController.getCurrentBackStackEntry() == null) return;
        if (!navController.popBackStack(startId, true)) {
            while (navController.getCurrentBackStackEntry() != null) {
                if (!navController.popBackStack()) break;
            }
        }
    }

    // ── One-handed mode ───────────────────────────────────────────────────────

    private void setupOneHandedMode() {
        View rootView = findViewById(R.id.main);
        View handle   = findViewById(R.id.oneHandedHandle);

        bottomNav.setOnTouchListener(new View.OnTouchListener() {
            private float startY = 0f;
            private boolean consumingSwipe = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getY();
                        consumingSwipe = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getY() - startY) > 8) consumingSwipe = true;
                        return consumingSwipe;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        boolean was = consumingSwipe;
                        consumingSwipe = false;
                        return was;
                    default:
                        return false;
                }
            }
        });

        if (handle != null) {
            handle.setOnClickListener(v -> deactivateOneHanded(rootView, handle));
        }
    }

    private void activateOneHanded(View rootView, View handle) {
        isOneHandedActive = true;
        float shift = getResources().getDisplayMetrics().heightPixels * 0.42f;
        rootView.animate().translationY(shift).setDuration(280).start();

        if (handle != null) {
            handle.post(() -> {
                int screenH = getResources().getDisplayMetrics().heightPixels;
                float margin = 6 * getResources().getDisplayMetrics().density;
                float defaultY = screenH - handle.getHeight();
                float targetY  = shift - handle.getHeight() - margin;
                handle.setTranslationY(targetY - defaultY);
                handle.setAlpha(0f);
                handle.setVisibility(View.VISIBLE);
                handle.animate().alpha(1f).setDuration(220)
                        .withEndAction(() -> startChevronAnimation(handle))
                        .start();
            });
        }
    }

    private void deactivateOneHanded(View rootView, View handle) {
        isOneHandedActive = false;
        stopChevronAnimation();
        if (rootView == null) rootView = findViewById(R.id.main);
        if (rootView != null) rootView.animate().translationY(0f).setDuration(280).start();

        if (handle == null) handle = findViewById(R.id.oneHandedHandle);
        if (handle != null) {
            final View h = handle;
            h.animate().alpha(0f).setDuration(180)
                    .withEndAction(() -> {
                        h.setVisibility(View.GONE);
                        h.setAlpha(1f);
                        if (h instanceof LinearLayout) {
                            float[] rest = {1.0f, 0.6f, 0.3f, 0.1f};
                            LinearLayout ll = (LinearLayout) h;
                            for (int i = 0; i < Math.min(ll.getChildCount(), rest.length); i++)
                                ll.getChildAt(i).setAlpha(rest[i]);
                        }
                    }).start();
        }
    }

    private void deactivateOneHanded() {
        deactivateOneHanded(findViewById(R.id.main), findViewById(R.id.oneHandedHandle));
    }

    private void startChevronAnimation(View handle) {
        if (!(handle instanceof LinearLayout)) return;
        LinearLayout group = (LinearLayout) handle;
        if (group.getChildCount() < 4) return;

        stopChevronAnimation();

        final float[] rest = {1.0f, 0.6f, 0.3f, 0.1f};

        Runnable[] loop = new Runnable[1];
        loop[0] = () -> {
            if (!handle.isAttachedToWindow() || handle.getVisibility() != View.VISIBLE) return;

            ObjectAnimator[] anims = new ObjectAnimator[4];
            int[] waveOrder = {3, 2, 1, 0};
            for (int k = 0; k < 4; k++) {
                int idx = waveOrder[k];
                anims[k] = ObjectAnimator.ofFloat(
                        group.getChildAt(idx), "alpha", rest[idx], 1.0f, rest[idx]);
                anims[k].setDuration(380);
                anims[k].setStartDelay(k * 120L);
            }

            AnimatorSet pass = new AnimatorSet();
            pass.playTogether(anims[0], anims[1], anims[2], anims[3]);
            pass.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    handle.postDelayed(loop[0], 350);
                }
            });

            chevronAnimator = pass;
            pass.start();
        };

        loop[0].run();
    }

    private void stopChevronAnimation() {
        if (chevronAnimator != null) {
            chevronAnimator.cancel();
            chevronAnimator = null;
        }
        View handle = findViewById(R.id.oneHandedHandle);
        if (handle != null && handle.getHandler() != null)
            handle.getHandler().removeCallbacksAndMessages(null);
    }

    // ── Swipe back / forward navigation ──────────────────────────────────────

    /**
     * Left-edge → swipe RIGHT  : go back (pop NavController stack, or return home).
     *
     * Edge zone is 64 dp from the left side so horizontal RecyclerViews in the
     * centre of the screen are never accidentally triggered.
     * Swipes starting inside the bottom-nav bar are ignored (handled by the
     * one-handed detector instead).
     */
    private void setupNavSwipeGesture() {
        float edgePx = 64 * getResources().getDisplayMetrics().density;

        navSwipeDetector = new GestureDetectorCompat(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dy = e2.getY() - e1.getY();

                        if (Math.abs(dy) > Math.abs(dx) * 0.75f) return false;
                        if (Math.abs(velocityX) < 300 || Math.abs(dx) < 60) return false;

                        if (bottomNav.getVisibility() == View.VISIBLE) {
                            int[] loc = new int[2];
                            bottomNav.getLocationOnScreen(loc);
                            if (e1.getRawY() >= loc[1]) return false;
                        }

                        float startX = e1.getRawX();
                        float screenW = getResources().getDisplayMetrics().widthPixels;

                        if (dx > 0 && startX <= edgePx) {
                            if (navHostFragment.getVisibility() == View.VISIBLE) {
                                getOnBackPressedDispatcher().onBackPressed();
                            } else {
                                shiftTab(-1);
                            }
                            return true;
                        }

                        return false;
                    }
                });
    }

    private void shiftTab(int delta) {
        android.view.Menu menu = bottomNav.getMenu();
        int count = menu.size();
        int currentId = bottomNav.getSelectedItemId();
        int currentIndex = -1;
        for (int i = 0; i < count; i++) {
            if (menu.getItem(i).getItemId() == currentId) { currentIndex = i; break; }
        }
        if (currentIndex == -1) return;
        int newIndex = currentIndex + delta;
        if (newIndex < 0 || newIndex >= count) return;
        bottomNav.setSelectedItemId(menu.getItem(newIndex).getItemId());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (navSwipeDetector != null) navSwipeDetector.onTouchEvent(ev);

        int screenH = getResources().getDisplayMetrics().heightPixels;
        float bottomZone = screenH * 0.28f;

        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (ev.getRawY() >= screenH - bottomZone) {
                    ohTouchStartX  = ev.getRawX();
                    ohTouchStartY  = ev.getRawY();
                    ohTouchStartMs = System.currentTimeMillis();
                } else {
                    ohTouchStartY = -1f;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (ohTouchStartY >= 0) {
                    float dx       = ev.getRawX() - ohTouchStartX;
                    float dy       = ev.getRawY() - ohTouchStartY;
                    long  duration = System.currentTimeMillis() - ohTouchStartMs;

                    boolean primarilyVertical = Math.abs(dy) > Math.abs(dx) * 0.5f;
                    boolean withinTime        = duration <= 700;

                    if (primarilyVertical && withinTime) {
                        if (dy >= 40 && !isOneHandedActive
                                && new AccessibilityHelper(this).isOneHandedMode()) {
                            activateOneHanded(
                                    findViewById(R.id.main),
                                    findViewById(R.id.oneHandedHandle));
                        } else if (dy <= -40 && isOneHandedActive) {
                            deactivateOneHanded();
                        }
                    }
                    ohTouchStartY = -1f;
                }
                break;

            case MotionEvent.ACTION_CANCEL:
                ohTouchStartY = -1f;
                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        if (eventsListener != null) eventsListener.remove();
        if (isFinishing()) {
            if (userRepository != null) userRepository.shutdown();
            if (eventRepository != null) eventRepository.shutdown();
        }
        super.onDestroy();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    /**
     * Called by ProfileFragment when an admin switches their view mode.
     * Updates the bottom nav and event adapter to reflect the chosen role.
     *
     * @param role "entrant" | "organizer" | "admin"
     */
    public void setEffectiveRole(String role) {
        userRole = normalizeAppRole(role);
        runOnUiThread(() -> {
            setupBottomNav();
            applyFilters();
        });
    }

    public String getEffectiveRole() { return userRole; }

    public boolean isGuestSession() { return isGuest; }

    /**
     * Called by ProfileFragment when the user signs out.
     */
    public void onUserLoggedOut() {
        userRole = "entrant";
        isGuest = true;
        bottomNavRole = null;
        resolvedUserKey = null;

        getSharedPreferences(GuestSignUpFragment.PREFS_GUEST, MODE_PRIVATE)
                .edit()
                .remove(GuestSignUpFragment.PREF_GUEST_EMAIL)
                .apply();

        runOnUiThread(() -> {
            setupBottomNav();
            if (!allEvents.isEmpty()) applyFilters();
            showHome();
        });
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

    public void setBottomNavVisible(boolean visible) {
        bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}