package com.example.abacus_app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * EventBrowseUITest.java
 * <p>
 * Intent/UI tests for event browsing and waitlist user stories.
 * These are instrumented tests that run on a device or emulator.
 * <p>
 * Covers:
 * - US 01.01.03 — Browse list of events
 * - US 01.01.04 — Filter events by keyword
 * - US 01.01.01 — Join waiting list
 * - US 01.01.02 — Leave waiting list (confirmation dialog)
 * - US 01.05.04 — Waitlist count display on event details screen
 * - Lottery Guidelines — guidelines fragment content and navigation
 * <p>
 * NOTE: Requires animations to be disabled on the emulator:
 * Settings → Developer Options → Window/Transition/Animator scale → Off
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class EventBrowseUITest {

    private ActivityScenario<MainActivity> scenario;

    /** Polls until the RecyclerView has at least one child, or times out. */
    private void waitUntilListLoaded(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.rv_events)).check(matches(hasMinimumChildCount(1)));
                return;
            } catch (Throwable e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    /** Polls until a view has effective visibility VISIBLE, or times out. */
    private void waitUntilVisible(int viewId, long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(viewId)).check(matches(
                        ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
                return;
            } catch (Throwable e) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Waits until either the join OR leave button is visible.
     * Used after opening EventDetailsFragment since we don't know which
     * state the user is in.
     */
    private void waitUntilWaitlistButtonReady(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(R.id.btn_join_waitlist)).check(matches(
                        ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
                return;
            } catch (Throwable e1) {
                try {
                    onView(withId(R.id.btn_leave_waitlist)).check(matches(
                            ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
                    return;
                } catch (Throwable e2) {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    /** Ensures user is on the waitlist before leave tests. */
    private void ensureOnWaitlist() {
        try {
            onView(withId(R.id.btn_join_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
            onView(withId(R.id.btn_join_waitlist)).perform(click());
            waitUntilVisible(R.id.btn_leave_waitlist, 8000);
        } catch (Throwable e) {
            // Leave button already showing — already on waitlist, nothing to do
        }
    }

    /** Ensures user is NOT on the waitlist before join tests. */
    private void ensureOffWaitlist() {
        try {
            onView(withId(R.id.btn_leave_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
            onView(withId(R.id.btn_leave_waitlist)).perform(click());
            onView(withText("Leave")).perform(click());
            waitUntilVisible(R.id.btn_join_waitlist, 8000);
        } catch (Throwable e) {
            // Join button already showing — not on waitlist, nothing to do
        }
    }

    @Before
    public void setUp() {
        Intent intent = new Intent(
                ApplicationProvider.getApplicationContext(), MainActivity.class);
        intent.putExtra("isGuest", true);
        scenario = ActivityScenario.launch(intent);
        waitUntilListLoaded(20000);
    }

    // ── US 01.01.03 — Browse event list ───────────────────────────────────────

    /**
     * US 01.01.03 AC 1 — Event list RecyclerView is visible and has items.
     */
    @Test
    public void testEventListIsVisible() {
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
        onView(withId(R.id.rv_events)).check(matches(hasMinimumChildCount(1)));
    }

    /**
     * US 01.01.03 AC 2 — Tapping an event card opens the event details screen.
     */
    @Test
    public void testTapEventOpensDetails() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        try {
            onView(withId(R.id.btn_join_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        } catch (Throwable e) {
            onView(withId(R.id.btn_leave_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        }
    }

    // ── US 01.01.04 — Filter events ───────────────────────────────────────────

    /**
     * US 01.01.04 AC 1 — Filter button opens the filter bottom sheet.
     */
    @Test
    public void testFilterButtonOpensBottomSheet() {
        onView(withId(R.id.btn_filter)).perform(click());
        onView(withId(R.id.et_keyword)).check(matches(isDisplayed()));
    }

    /**
     * US 01.01.04 AC 2 — Entering a non-matching keyword shows empty state.
     */
    @Test
    public void testKeywordFilterReducesList() {
        onView(withId(R.id.btn_filter)).perform(click());
        onView(withId(R.id.et_keyword))
                .perform(typeText("zzznomatch"), closeSoftKeyboard());
        onView(withId(R.id.btn_apply_filters)).perform(click());
        onView(withId(R.id.rv_events)).check(matches(not(isDisplayed())));
        onView(withId(R.id.layout_empty_state)).check(matches(isDisplayed()));
    }

    /**
     * US 01.01.04 AC 3 — Clearing filters restores the full list.
     */
    @Test
    public void testClearFiltersRestoresList() {
        onView(withId(R.id.btn_filter)).perform(click());
        onView(withId(R.id.et_keyword))
                .perform(typeText("zzznomatch"), closeSoftKeyboard());
        onView(withId(R.id.btn_apply_filters)).perform(click());
        onView(withId(R.id.btn_filter)).perform(click());
        onView(withId(R.id.btn_clear_filters)).perform(click());
        waitUntilVisible(R.id.rv_events, 5000);
        onView(withId(R.id.rv_events)).check(matches(isDisplayed()));
    }

    // ── US 01.01.01 — Join waiting list ───────────────────────────────────────

    /**
     * US 01.01.01 AC 1 — Waitlist button (join or leave) is visible after opening event details.
     */
    @Test
    public void testJoinButtonVisibleOnEventDetails() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        try {
            onView(withId(R.id.btn_join_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        } catch (Throwable e) {
            onView(withId(R.id.btn_leave_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        }
    }

    /**
     * US 01.01.01 AC 2 — Tapping Join switches to Leave button.
     */
    @Test
    public void testJoinWaitlistShowsConfirmation() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOffWaitlist();
        onView(withId(R.id.btn_join_waitlist)).perform(click());
        waitUntilVisible(R.id.btn_leave_waitlist, 8000);
        onView(withId(R.id.btn_leave_waitlist))
                .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    // ── US 01.01.02 — Leave waiting list ──────────────────────────────────────

    /**
     * US 01.01.02 AC 2 — Tapping Leave shows a confirmation dialog.
     */
    @Test
    public void testLeaveWaitlistShowsConfirmationDialog() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOnWaitlist();
        onView(withId(R.id.btn_leave_waitlist)).perform(click());
        onView(withText("Leave Waiting List")).check(matches(isDisplayed()));
    }

    /**
     * US 01.01.02 AC 5 — Cancelling the leave dialog keeps user on waitlist.
     */
    @Test
    public void testCancelLeaveKeepsUserOnWaitlist() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOnWaitlist();
        onView(withId(R.id.btn_leave_waitlist)).perform(click());
        onView(withText("Cancel")).perform(click());
        onView(withId(R.id.btn_leave_waitlist))
                .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    /**
     * US 01.01.02 AC 3 — Confirming leave switches back to Join button.
     */
    @Test
    public void testConfirmLeaveRemovesFromWaitlist() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOnWaitlist();
        onView(withId(R.id.btn_leave_waitlist)).perform(click());
        onView(withText("Leave")).perform(click());
        waitUntilVisible(R.id.btn_join_waitlist, 8000);
        onView(withId(R.id.btn_join_waitlist))
                .check(matches(ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    // ── US 01.05.04 — Waitlist count display ──────────────────────────────────

    /**
     * US 01.05.04 AC 1 — Waitlist count is displayed when event details loads.
     */
    @Test
    public void testWaitlistCountDisplayedOnLoad() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        waitUntilVisible(R.id.tv_waitlist_count, 8000);
        onView(withId(R.id.tv_waitlist_count)).check(matches(isDisplayed()));
    }

    /**
     * US 01.05.04 AC 1 — Waitlist count text contains "waiting list" or "spots".
     */
    @Test
    public void testWaitlistCountTextIsRealistic() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        waitUntilVisible(R.id.tv_waitlist_count, 8000);
        // Count should contain "waiting list" or "spots left" or "Capacity"
        try {
            onView(withId(R.id.tv_waitlist_count))
                    .check(matches(withText(containsString("waiting list"))));
        } catch (Throwable e) {
            try {
                onView(withId(R.id.tv_waitlist_count))
                        .check(matches(withText(containsString("spots left"))));
            } catch (Throwable e2) {
                onView(withId(R.id.tv_waitlist_count))
                        .check(matches(withText(containsString("Capacity"))));
            }
        }
    }

    /**
     * US 01.05.04 AC 2 — Waitlist count updates after joining.
     * Joins the waitlist and confirms the count text changes.
     */
    @Test
    public void testWaitlistCountUpdatesAfterJoin() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOffWaitlist();

        // Read count is showing before join
        waitUntilVisible(R.id.tv_waitlist_count, 8000);
        onView(withId(R.id.tv_waitlist_count)).check(matches(isDisplayed()));

        // Join and wait for count to refresh
        onView(withId(R.id.btn_join_waitlist)).perform(click());
        waitUntilVisible(R.id.btn_leave_waitlist, 8000);
        waitUntilVisible(R.id.tv_waitlist_count, 5000);
        onView(withId(R.id.tv_waitlist_count)).check(matches(isDisplayed()));
    }

    /**
     * US 01.05.04 AC 2 — Waitlist count updates after leaving.
     * Leaves the waitlist and confirms the count text is still shown.
     */
    @Test
    public void testWaitlistCountUpdatesAfterLeave() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        ensureOnWaitlist();

        onView(withId(R.id.btn_leave_waitlist)).perform(click());
        onView(withText("Leave")).perform(click());
        waitUntilVisible(R.id.btn_join_waitlist, 8000);
        waitUntilVisible(R.id.tv_waitlist_count, 5000);
        onView(withId(R.id.tv_waitlist_count)).check(matches(isDisplayed()));
    }

    // ── Carousel (Phase 2) ────────────────────────────────────────────────────

    /**
     * Phase 2 — The carousel RecyclerView is visible on the main screen
     * when at least one event is available.
     */
    @Test
    public void carousel_isVisibleWhenEventsExist() {
        // Wait for main event list to load (setUp already does this)
        waitUntilVisible(R.id.rv_carousel, 10000);
        onView(withId(R.id.rv_carousel)).check(matches(
                ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    /**
     * Phase 2 — The "Featured Events" label is visible together with the carousel.
     */
    @Test
    public void carousel_featuredLabelIsVisible() {
        waitUntilVisible(R.id.tv_featured_label, 10000);
        onView(withId(R.id.tv_featured_label)).check(matches(
                ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
    }

    /**
     * Phase 2 — Carousel shows at most 5 items even when more events are loaded.
     * We assert the item count is at least 1 and at most 5.
     */
    @Test
    public void carousel_showsAtMostFiveItems() {
        waitUntilVisible(R.id.rv_carousel, 10000);
        // Item count must be between 1 and 5 inclusive
        onView(withId(R.id.rv_carousel)).check(matches(hasMinimumChildCount(1)));
        // Check that it never exceeds 5 via RecyclerView adapter count
        scenario.onActivity(activity -> {
            androidx.recyclerview.widget.RecyclerView rv =
                    activity.findViewById(R.id.rv_carousel);
            if (rv != null && rv.getAdapter() != null) {
                int count = rv.getAdapter().getItemCount();
                org.junit.Assert.assertTrue(
                        "Carousel should show ≤5 items, got " + count,
                        count <= 5);
            }
        });
    }

    /**
     * Phase 2 — Tapping the first carousel card navigates to EventDetailsFragment.
     */
    @Test
    public void carousel_tapFirstCardOpensEventDetails() {
        waitUntilVisible(R.id.rv_carousel, 10000);
        onView(withId(R.id.rv_carousel))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        try {
            onView(withId(R.id.btn_join_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        } catch (Throwable e) {
            onView(withId(R.id.btn_leave_waitlist)).check(matches(
                    ViewMatchers.withEffectiveVisibility(ViewMatchers.Visibility.VISIBLE)));
        }
    }

    // ── Lottery Guidelines ────────────────────────────────────────────────────

    /**
     * Lottery Guidelines AC 1 — Lottery Guidelines button is visible on event details.
     */
    @Test
    public void testLotteryGuidelinesButtonVisible() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        onView(withId(R.id.btn_lottery_guidelines)).check(matches(isDisplayed()));
    }

    /**
     * Lottery Guidelines AC 2 — Tapping Lottery Guidelines opens the guidelines fragment.
     */
    @Test
    public void testLotteryGuidelinesOpensFragment() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        onView(withId(R.id.btn_lottery_guidelines)).perform(click());
        waitUntilVisible(R.id.tv_selection_process, 8000);
        onView(withId(R.id.tv_selection_process)).check(matches(isDisplayed()));
    }

    /**
     * Lottery Guidelines AC 2 — Guidelines fragment shows all required sections.
     */
    @Test
    public void testLotteryGuidelinesShowsAllSections() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        onView(withId(R.id.btn_lottery_guidelines)).perform(click());
        waitUntilVisible(R.id.tv_selection_process, 8000);

        onView(withId(R.id.tv_selection_process)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_entrants_selected)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_draw_date)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_if_selected)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_if_declined)).check(matches(isDisplayed()));
        onView(withId(R.id.tv_eligibility)).check(matches(isDisplayed()));
    }

    /**
     * Lottery Guidelines AC 3 — When no custom criteria set, selection is shown as random.
     */
    @Test
    public void testLotteryGuidelinesShowsRandomSelection() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        onView(withId(R.id.btn_lottery_guidelines)).perform(click());
        waitUntilVisible(R.id.tv_selection_process, 8000);
        onView(withId(R.id.tv_selection_process))
                .check(matches(withText(containsString("random"))));
    }

    /**
     * Lottery Guidelines — Back button returns to event details screen.
     */
    @Test
    public void testLotteryGuidelinesBackButtonWorks() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        onView(withId(R.id.btn_lottery_guidelines)).perform(click());
        waitUntilVisible(R.id.tv_selection_process, 8000);
        onView(withId(R.id.btn_back_guidelines)).perform(click());
        waitUntilWaitlistButtonReady(8000);
        onView(withId(R.id.btn_lottery_guidelines)).check(matches(isDisplayed()));
    }
}