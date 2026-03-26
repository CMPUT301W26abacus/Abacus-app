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
 * UI Tests for the Notification subsystem.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class NotificationUITest {

    @Rule
    public ActivityScenarioRule<MainActivity> activityRule =
            new ActivityScenarioRule<>(MainActivity.class);

    @Test
    public void testNavigationToInbox() {
        // Click on the Inbox item in the Bottom Navigation Bar
        onView(withId(R.id.nav_inbox)).perform(click());

        // Check if the fragment title "Notifications" is displayed.
        onView(withText("Notifications")).check(matches(isDisplayed()));
        
        // Verify the recycler view is visible
        onView(withId(R.id.notificationRecycler)).check(matches(isDisplayed()));
    }

    @Test
    public void testNotificationListPresence() {
        onView(withId(R.id.nav_inbox)).perform(click());
        
        // The list should be present even if empty
        onView(withId(R.id.notificationRecycler)).check(matches(isDisplayed()));
    }
}
