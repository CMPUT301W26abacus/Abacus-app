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
 *
 * Intent/UI tests for event browsing and waitlist user stories.
 * These are instrumented tests that run on a device or emulator.
 *
 * Covers:
 * - US 01.01.03 — Browse list of events
 * - US 01.01.04 — Filter events by keyword
 * - US 01.01.01 — Join waiting list
 * - US 01.01.02 — Leave waiting list (confirmation dialog)
 *
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
            // Join button is showing — tap it to join
            onView(withId(R.id.btn_join_waitlist)).perform(click());
            waitUntilVisible(R.id.btn_leave_waitlist, 8000);
        } catch (Throwable e) {
            // Leave button already showing — already on waitlist, nothing to do
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
        // Either join or leave is visible — details screen loaded successfully
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
     * Shows join if not yet on waitlist, leave if already joined.
     */
    @Test
    public void testJoinButtonVisibleOnEventDetails() {
        onView(withId(R.id.rv_events))
                .perform(actionOnItemAtPosition(0, click()));
        waitUntilWaitlistButtonReady(15000);
        // Either join or leave must be visible — confirms details screen loaded correctly
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
        waitUntilVisible(R.id.btn_join_waitlist, 15000);
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
}