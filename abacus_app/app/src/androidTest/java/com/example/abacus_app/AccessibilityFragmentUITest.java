package com.example.abacus_app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isNotChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso UI tests for {@link AccessibilityFragment}.
 *
 * Uses {@link FragmentScenario} — no Firebase connection needed.
 * Tests verify:
 *   - Both switches start unchecked when SharedPreferences are clear
 *   - One-handed mode switch reflects persisted state (true)
 *   - Large-text switch reflects persisted state (true)
 *   - Back button is visible
 *
 * Note: toggling a switch calls {@code recreate()} which exits the current
 * FragmentScenario context, so toggle-persistence tests use the SharedPreferences
 * backing store directly rather than Espresso click actions.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityFragmentUITest {

    private static final String PREFS_NAME = "accessibility_prefs";

    private FragmentScenario<AccessibilityFragment> scenario;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        scenario = FragmentScenario.launchInContainer(
                AccessibilityFragment.class,
                null,
                R.style.Theme_Abacusapp,
                (androidx.fragment.app.FragmentFactory) null);
    }

    @After
    public void tearDown() {
        prefs.edit().clear().commit();
        if (scenario != null) scenario.close();
    }

    // ── Layout sanity ─────────────────────────────────────────────────────────

    @Test
    public void fragment_launchesWithoutCrashing() {
        onView(withId(R.id.switchOneHanded)).check(matches(isDisplayed()));
    }

    @Test
    public void backButton_isVisible() {
        onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
    }

    // ── Initial switch state ──────────────────────────────────────────────────

    @Test
    public void switchesStartUnchecked_whenPrefsAreClear() {
        onView(withId(R.id.switchOneHanded)).check(matches(isNotChecked()));
        onView(withId(R.id.switchLargeText)).check(matches(isNotChecked()));
    }

    // ── Persisted state reflected in UI ──────────────────────────────────────

    @Test
    public void oneHandedSwitch_isChecked_whenPrefsSetToTrue() {
        // Pre-set the preference before launching the fragment
        prefs.edit().putBoolean("oneHandedMode", true).commit();
        if (scenario != null) scenario.close();

        scenario = FragmentScenario.launchInContainer(
                AccessibilityFragment.class,
                null,
                R.style.Theme_Abacusapp,
                (androidx.fragment.app.FragmentFactory) null);

        onView(withId(R.id.switchOneHanded)).check(matches(isChecked()));
    }

    @Test
    public void largeTextSwitch_isChecked_whenPrefsSetToTrue() {
        prefs.edit().putBoolean("largeText", true).commit();
        if (scenario != null) scenario.close();

        scenario = FragmentScenario.launchInContainer(
                AccessibilityFragment.class,
                null,
                R.style.Theme_Abacusapp,
                (androidx.fragment.app.FragmentFactory) null);

        onView(withId(R.id.switchLargeText)).check(matches(isChecked()));
    }

    @Test
    public void oneHandedSwitch_isNotChecked_whenPrefsSetToFalse() {
        prefs.edit().putBoolean("oneHandedMode", false).commit();
        if (scenario != null) scenario.close();

        scenario = FragmentScenario.launchInContainer(
                AccessibilityFragment.class,
                null,
                R.style.Theme_Abacusapp,
                (androidx.fragment.app.FragmentFactory) null);

        onView(withId(R.id.switchOneHanded)).check(matches(isNotChecked()));
    }

    // ── Both switches visible ─────────────────────────────────────────────────

    @Test
    public void largeTextSwitch_isDisplayed() {
        onView(withId(R.id.switchLargeText)).check(matches(isDisplayed()));
    }
}
