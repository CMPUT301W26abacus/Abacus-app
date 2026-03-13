package com.example.abacus_app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Intent and User Interface Tests for the Notification subsystem.
 * This class verifies the end-to-end user experience related to notifications,
 * ensuring that the navigation flows and UI components behave as expected.
 *
 * Requirements Covered:
 * - US 01.04.01: Receive notification when chosen (win).
 * - US 01.04.02: Receive notification when not chosen (lose).
 *
 * Design Pattern: Instrumentation Testing / UI Testing (Espresso).
 *
 * Outstanding Issues:
 * - Tests do not currently verify the content of individual notification items.
 * - Reliance on real device state/Firestore means tests may be flaky if network is unstable.
 * - Does not mock Firestore, so it requires existing data or a clean environment.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class NotificationUITest {

    /**
     * Rule that launches the {@link MainActivity} before each test.
     */
    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    /**
     * Verifies that the user can navigate to the Inbox (Notification) screen via the Bottom Navigation Bar.
     * Checks for the presence of the fragment title and the list container.
     */
    @Test
    public void testNavigationToInbox() {
        // Click on the Inbox item in the Bottom Navigation Bar
        onView(withId(R.id.nav_inbox)).perform(click());

        // Check if the fragment title "Notifications" is displayed.
        // We use "Notifications" because that is the text in main_inbox_fragment.xml.
        // This avoids ambiguity with the "Inbox" labels in the BottomNavigationView.
        onView(withText("Notifications")).check(matches(isDisplayed()));
        
        // Verify the recycler view is visible
        onView(withId(R.id.notificationRecycler)).check(matches(isDisplayed()));
    }

    /**
     * Verifies that the notification list (RecyclerView) is present on the screen after navigating to the Inbox.
     * This covers the container requirement for US 01.04.01 and US 01.04.02.
     */
    @Test
    public void testNotificationListPresence() {
        onView(withId(R.id.nav_inbox)).perform(click());
        
        // The list should be present even if empty
        onView(withId(R.id.notificationRecycler)).check(matches(isDisplayed()));
    }
}
