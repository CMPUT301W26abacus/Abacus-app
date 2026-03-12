package com.example.abacus_app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static com.example.abacus_app.UserLocalDataSource.KEY_UUID;
import static com.example.abacus_app.UserLocalDataSource.PREFS_NAME;
import static org.hamcrest.Matchers.not;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class SplashActivityUITest {
    private Context context;

    /**
     * Clear DataStore before each test to ensure a known starting state.
     * Deleting the datastore folder simulates a fresh install.
     */
    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        clearDataStore();
    }

    // -------------------------------------------------------------------------
    // First-time user — no UUID in DataStore
    // -------------------------------------------------------------------------

    /**
     * On first launch, both CTA buttons must be invisible before the
     * reveal delay fires.
     */
    @Test
    public void firstLaunch_buttonsAreInitiallyInvisible() {
        ActivityScenario.launch(SplashActivity.class);

        onView(withId(R.id.btnGetStarted))
                .check(matches(not(isDisplayed())));
        onView(withId(R.id.tvBrowseGuest))
                .check(matches(not(isDisplayed())));
    }

    /**
     * On first launch, after BUTTONS_REVEAL_DELAY_MS (1200ms) + animation,
     * both buttons must be visible.
     */
    @Test
    public void firstLaunch_buttonsAppearAfterDelay() throws InterruptedException {
        ActivityScenario.launch(SplashActivity.class);

        Thread.sleep(1800);

        onView(withId(R.id.btnGetStarted))
                .check(matches(isDisplayed()));
        onView(withId(R.id.tvBrowseGuest))
                .check(matches(isDisplayed()));
    }

    /**
     * "Get Started" button must show the correct label text.
     */
    @Test
    public void firstLaunch_getStartedButtonHasCorrectText() throws InterruptedException {
        ActivityScenario.launch(SplashActivity.class);
        Thread.sleep(1800);

        onView(withId(R.id.btnGetStarted))
                .check(matches(withText("Get Started")));
    }

    /**
     * "Browse as guest" link must show the correct label text.
     */
    @Test
    public void firstLaunch_browseGuestHasCorrectText() throws InterruptedException {
        ActivityScenario.launch(SplashActivity.class);
        Thread.sleep(1800);

        onView(withId(R.id.tvBrowseGuest))
                .check(matches(withText("Browse events as guest")));
    }

    /**
     * Tapping "Get Started" must open LoginActivity (email field becomes visible).
     */
    @Test
    public void firstLaunch_getStartedNavigatesToLogin() throws InterruptedException {
        ActivityScenario.launch(SplashActivity.class);
        Thread.sleep(1800);

        onView(withId(R.id.btnGetStarted)).perform(click());

        onView(withId(R.id.etEmail)).check(matches(isDisplayed()));
    }

    /**
     * Tapping "Browse as guest" must open MainActivity (bottom nav becomes visible).
     */
    @Test
    public void firstLaunch_browseGuestNavigatesToMain() throws InterruptedException {
        ActivityScenario.launch(SplashActivity.class);
        Thread.sleep(1800);

        onView(withId(R.id.tvBrowseGuest)).perform(click());

        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
    }

    // -------------------------------------------------------------------------
    // Returning user — UUID already in DataStore
    // -------------------------------------------------------------------------

    /**
     * When a UUID is seeded before launch, buttons must never appear.
     * The splash should skip straight to MainActivity.
     */
    @Test
    public void returningUser_buttonsNeverAppear() throws InterruptedException {
        seedUUID("returning-user-uuid-123");
        ActivityScenario.launch(SplashActivity.class);

        Thread.sleep(1500);

        onView(withId(R.id.btnGetStarted))
                .check(matches(not(isDisplayed())));
        onView(withId(R.id.tvBrowseGuest))
                .check(matches(not(isDisplayed())));
    }

    /**
     * When a UUID is seeded, MainActivity must be launched automatically
     * after ANIMATION_DELAY_MS without any user interaction.
     */
    @Test
    public void returningUser_navigatesToMainAutomatically() throws InterruptedException {
        seedUUID("returning-user-uuid-456");
        ActivityScenario.launch(SplashActivity.class);

        Thread.sleep(2200);

        onView(withId(R.id.bottom_nav)).check(matches(isDisplayed()));
    }

    // -------------------------------------------------------------------------
    // Logo visibility
    // -------------------------------------------------------------------------

    /**
     * The animated abacus logo must always be visible on the splash screen
     * regardless of user state.
     */
    @Test
    public void splashLogo_isAlwaysVisible() {
        ActivityScenario.launch(SplashActivity.class);

        onView(withId(R.id.splashAbacus)).check(matches(isDisplayed()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a UUID into DataStore before launching the activity,
     * simulating a returning user who already has a device UUID stored.
     *
     * @param uuid The UUID string to seed into DataStore.
     */
    private void seedUUID(String uuid) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_UUID, uuid)
                .commit(); // commit() (not apply()) so it's synchronous and ready before launch
    }

    /**
     * Deletes the DataStore preferences file to simulate a fresh install.
     * Called before each test via @Before to ensure test isolation.
     */
    private void clearDataStore() {
        java.io.File dataStoreDir = new java.io.File(context.getFilesDir(), "datastore");
        if (dataStoreDir.exists()) {
            for (java.io.File file : dataStoreDir.listFiles()) {
                file.delete();
            }
        }
    }
}
