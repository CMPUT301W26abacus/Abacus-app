package com.example.abacus_app;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.not;

/**
 * Espresso tests for {@link RegistrationHistoryFragment}.
 *
 * Tests cover:
 *   - Fragment inflates without crashing.
 *   - Empty state layout is visible when there are no registrations.
 *   - RecyclerView is visible when registrations are present.
 *   - RecyclerView is hidden when the list is empty.
 *   - Swipe-to-refresh does not crash.
 *
 * The fragment builds its own {@link RegistrationHistoryViewModel} via a factory,
 * so we seed the ViewModel's LiveData directly through the fragment's
 * {@code viewModel} field using reflection in order to control displayed state
 * without a Firebase connection.
 *
 * Ref: US 01.05.01–04
 */
@RunWith(AndroidJUnit4.class)
public class RegistrationHistoryFragmentTest {

    private FragmentScenario<RegistrationHistoryFragment> scenario;

    // Fixed epoch for "2026-03-11 00:00:00 UTC"
    private static final long TIMESTAMP = 1741651200000L;

    @Before
    public void setUp() {
        scenario = FragmentScenario.launchInContainer(
                RegistrationHistoryFragment.class,
                /*fragmentArgs=*/ null,
                R.style.Theme_Abacusapp,
                /*factory=*/ null);
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
    }

    // ── Inflation ────────────────────────────────────────────────────────────

    /**
     * Fragment inflates and displays the SwipeRefreshLayout root without crashing.
     */
    @Test
    public void fragment_launchesWithoutCrashing() {
        onView(withId(R.id.swipe_refresh)).check(matches(isDisplayed()));
    }

    // ── Empty state — US 01.05.01 ─────────────────────────────────────────────

    /**
     * US 01.05.01 — The empty-state layout is shown when the registrations list
     * is empty (e.g. user has never joined any event).
     */
    @Test
    public void emptyList_showsEmptyState() {
        postRegistrations(new ArrayList<>());

        onView(withId(R.id.layout_empty_state)).check(matches(isDisplayed()));
    }

    /**
     * US 01.05.01 — The RecyclerView is hidden when the list is empty.
     */
    @Test
    public void emptyList_hidesRecyclerView() {
        postRegistrations(new ArrayList<>());

        onView(withId(R.id.rv_registrations)).check(matches(not(isDisplayed())));
    }

    /**
     * US 01.05.01 — The empty-state title string is visible.
     */
    @Test
    public void emptyList_emptyStateTitleVisible() {
        postRegistrations(new ArrayList<>());

        onView(withId(R.id.tv_empty_state_title)).check(matches(isDisplayed()));
    }

    // ── Populated list — US 01.05.02 ─────────────────────────────────────────

    /**
     * US 01.05.02 — The RecyclerView is visible when registrations are present.
     */
    @Test
    public void populatedList_showsRecyclerView() {
        postRegistrations(Arrays.asList(
                makeItem("event-1", "Spring Gala 2026",  "On Waitlist"),
                makeItem("event-2", "Summer Picnic 2026", "Enrolled")
        ));

        onView(withId(R.id.rv_registrations)).check(matches(isDisplayed()));
    }

    /**
     * US 01.05.02 — The empty-state layout is hidden when registrations exist.
     */
    @Test
    public void populatedList_hidesEmptyState() {
        postRegistrations(Arrays.asList(
                makeItem("event-1", "Spring Gala 2026", "On Waitlist")
        ));

        onView(withId(R.id.layout_empty_state)).check(matches(not(isDisplayed())));
    }

    /**
     * US 01.05.02 — The title of the first event is displayed in the RecyclerView.
     */
    @Test
    public void populatedList_firstEventTitleVisible() {
        postRegistrations(Arrays.asList(
                makeItem("event-1", "Spring Gala 2026", "On Waitlist")
        ));

        onView(withText("Spring Gala 2026")).check(matches(isDisplayed()));
    }

    /**
     * US 01.05.03 — The status label of the first event is displayed.
     */
    @Test
    public void populatedList_statusLabelVisible() {
        postRegistrations(Arrays.asList(
                makeItem("event-1", "Spring Gala 2026", "Selected!")
        ));

        onView(withText("Selected!")).check(matches(isDisplayed()));
    }

    // ── Progress bar ─────────────────────────────────────────────────────────

    /**
     * The progress bar is hidden once loading is complete (isLoading = false).
     */
    @Test
    public void afterLoading_progressBarHidden() {
        postIsLoading(false);

        onView(withId(R.id.progress_bar)).check(matches(not(isDisplayed())));
    }

    /**
     * The progress bar is visible while loading (isLoading = true).
     */
    @Test
    public void whileLoading_progressBarVisible() {
        postIsLoading(true);

        onView(withId(R.id.progress_bar)).check(matches(isDisplayed()));
    }

    // ── Swipe-to-refresh ─────────────────────────────────────────────────────

    /**
     * Swiping down on the list does not crash the fragment.
     */
    @Test
    public void swipeToRefresh_doesNotCrash() {
        postRegistrations(new ArrayList<>());

        onView(withId(R.id.swipe_refresh)).perform(swipeDown());

        // If we get here the fragment did not crash
        onView(withId(R.id.swipe_refresh)).check(matches(isDisplayed()));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RegistrationHistoryViewModel.RegistrationHistoryItem makeItem(
            String eventId, String title, String statusLabel) {
        return new RegistrationHistoryViewModel.RegistrationHistoryItem(
                eventId, title, null, statusLabel, TIMESTAMP);
    }

    /**
     * Posts a list of items to the fragment's ViewModel on the main thread via
     * reflection so the fragment reacts as if data arrived from Firestore.
     */
    private void postRegistrations(
            List<RegistrationHistoryViewModel.RegistrationHistoryItem> items) {
        scenario.onFragment(fragment -> {
            try {
                RegistrationHistoryViewModel vm = getViewModel(fragment);
                if (vm == null) return;
                java.lang.reflect.Field f =
                        RegistrationHistoryViewModel.class.getDeclaredField("registrations");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                MutableLiveData<List<RegistrationHistoryViewModel.RegistrationHistoryItem>> ld =
                        (MutableLiveData<List<RegistrationHistoryViewModel.RegistrationHistoryItem>>) f.get(vm);
                if (ld != null) ld.postValue(items);
            } catch (Exception e) {
                throw new RuntimeException("Could not seed registrations list", e);
            }
        });

        // Let the main thread settle
        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
    }

    /**
     * Posts an isLoading value to the fragment's ViewModel via reflection.
     */
    private void postIsLoading(boolean loading) {
        scenario.onFragment(fragment -> {
            try {
                RegistrationHistoryViewModel vm = getViewModel(fragment);
                if (vm == null) return;
                java.lang.reflect.Field f =
                        RegistrationHistoryViewModel.class.getDeclaredField("isLoading");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                MutableLiveData<Boolean> ld = (MutableLiveData<Boolean>) f.get(vm);
                if (ld != null) ld.postValue(loading);
            } catch (Exception e) {
                throw new RuntimeException("Could not seed isLoading", e);
            }
        });

        try { Thread.sleep(300); } catch (InterruptedException ignored) {}
    }

    /** Retrieves the private {@code viewModel} field from the fragment via reflection. */
    private RegistrationHistoryViewModel getViewModel(
            RegistrationHistoryFragment fragment) throws Exception {
        java.lang.reflect.Field f =
                RegistrationHistoryFragment.class.getDeclaredField("viewModel");
        f.setAccessible(true);
        return (RegistrationHistoryViewModel) f.get(fragment);
    }
}
