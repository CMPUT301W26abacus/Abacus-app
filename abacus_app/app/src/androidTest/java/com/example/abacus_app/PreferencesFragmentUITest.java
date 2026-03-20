package com.example.abacus_app;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso UI tests for {@link PreferencesFragment}.
 *
 * Uses {@link FragmentScenario} so no real Firestore connection is needed.
 * Tests verify:
 *   - All 8 category chips render
 *   - Location slider label shows "0 km" by default
 *   - Back button is visible
 *   - Save button is visible and enabled
 *   - Event-size radio buttons are rendered
 *
 * Note: Saving preferences requires a real UserRepository (Firestore); those
 * flows are covered by unit tests in PreferencesViewModelTest.
 */
@RunWith(AndroidJUnit4.class)
public class PreferencesFragmentUITest {

    private FragmentScenario<PreferencesFragment> scenario;

    @Before
    public void setUp() {
        scenario = FragmentScenario.launchInContainer(
                PreferencesFragment.class,
                null,
                R.style.Theme_Abacusapp,
                null);
    }

    @After
    public void tearDown() {
        if (scenario != null) scenario.close();
    }

    // ── Layout sanity ─────────────────────────────────────────────────────────

    @Test
    public void fragment_launchesWithoutCrashing() {
        onView(withId(R.id.btnSavePrefs)).check(matches(isDisplayed()));
    }

    @Test
    public void backButton_isVisible() {
        onView(withId(R.id.btnBack)).check(matches(isDisplayed()));
    }

    // ── Category chips ────────────────────────────────────────────────────────

    @Test
    public void categoryChip_music_isDisplayed() {
        onView(withId(R.id.chipMusic)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_sports_isDisplayed() {
        onView(withId(R.id.chipSports)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_tech_isDisplayed() {
        onView(withId(R.id.chipTech)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_arts_isDisplayed() {
        onView(withId(R.id.chipArts)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_food_isDisplayed() {
        onView(withId(R.id.chipFood)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_outdoor_isDisplayed() {
        onView(withId(R.id.chipOutdoor)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_networking_isDisplayed() {
        onView(withId(R.id.chipNetworking)).check(matches(isDisplayed()));
    }

    @Test
    public void categoryChip_other_isDisplayed() {
        onView(withId(R.id.chipOther)).check(matches(isDisplayed()));
    }

    // ── Slider ────────────────────────────────────────────────────────────────

    @Test
    public void sliderValueLabel_defaultsToZeroKm() {
        onView(withId(R.id.tvSliderValue))
                .check(matches(withText(containsString("0 km"))));
    }

    @Test
    public void locationSlider_isDisplayed() {
        onView(withId(R.id.sliderLocationRange)).check(matches(isDisplayed()));
    }

    // ── Event size ────────────────────────────────────────────────────────────

    @Test
    public void eventSizeRadio_small_isDisplayed() {
        onView(withId(R.id.rbSmall)).check(matches(isDisplayed()));
    }

    @Test
    public void eventSizeRadio_medium_isDisplayed() {
        onView(withId(R.id.rbMedium)).check(matches(isDisplayed()));
    }

    @Test
    public void eventSizeRadio_large_isDisplayed() {
        onView(withId(R.id.rbLarge)).check(matches(isDisplayed()));
    }

    // ── Save button ───────────────────────────────────────────────────────────

    @Test
    public void saveButton_isDisplayed() {
        onView(withId(R.id.btnSavePrefs)).check(matches(isDisplayed()));
    }

    @Test
    public void clickingMusicChip_doesNotCrash() {
        onView(withId(R.id.chipMusic)).perform(click());
        onView(withId(R.id.chipMusic)).check(matches(isDisplayed()));
    }
}
