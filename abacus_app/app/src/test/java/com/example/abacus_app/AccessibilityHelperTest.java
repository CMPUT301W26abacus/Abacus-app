package com.example.abacus_app;

import android.content.Context;
import android.content.res.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link AccessibilityHelper}.
 *
 * Uses Robolectric so that SharedPreferences works without a physical device.
 * Covers:
 *   - Default values (both flags off)
 *   - Persistence of colorBlindMode flag
 *   - Persistence of largeText flag
 *   - buildConfig large-text font scale (1.3f)
 *   - buildConfig normal font scale (1.0f)
 *   - joinButtonColor returns appropriate colour per mode
 *   - selectedStatusColor returns appropriate colour per mode
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class AccessibilityHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        // Clear prefs before each test for isolation
        context.getSharedPreferences("accessibility_prefs", Context.MODE_PRIVATE)
                .edit().clear().commit();
    }

    // ── Default state ────────────────────────────────────────────────────────

    @Test
    public void defaultsAreFalse_colorBlindMode() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        assertFalse(helper.isColorBlindMode());
    }

    @Test
    public void defaultsAreFalse_largeText() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        assertFalse(helper.isLargeText());
    }

    // ── ColorBlind persistence ────────────────────────────────────────────────

    @Test
    public void setColorBlindModeTrue_persistsAcrossInstances() {
        AccessibilityHelper helper1 = new AccessibilityHelper(context);
        helper1.setColorBlindMode(true);

        AccessibilityHelper helper2 = new AccessibilityHelper(context);
        assertTrue(helper2.isColorBlindMode());
    }

    @Test
    public void setColorBlindModeFalse_persistsAcrossInstances() {
        AccessibilityHelper helper1 = new AccessibilityHelper(context);
        helper1.setColorBlindMode(true);
        helper1.setColorBlindMode(false);

        AccessibilityHelper helper2 = new AccessibilityHelper(context);
        assertFalse(helper2.isColorBlindMode());
    }

    // ── LargeText persistence ─────────────────────────────────────────────────

    @Test
    public void setLargeTextTrue_persistsAcrossInstances() {
        AccessibilityHelper helper1 = new AccessibilityHelper(context);
        helper1.setLargeText(true);

        AccessibilityHelper helper2 = new AccessibilityHelper(context);
        assertTrue(helper2.isLargeText());
    }

    @Test
    public void setLargeTextFalse_persistsAcrossInstances() {
        AccessibilityHelper helper1 = new AccessibilityHelper(context);
        helper1.setLargeText(true);
        helper1.setLargeText(false);

        AccessibilityHelper helper2 = new AccessibilityHelper(context);
        assertFalse(helper2.isLargeText());
    }

    // ── buildConfig ───────────────────────────────────────────────────────────

    @Test
    public void buildConfig_largeTextTrue_sets1_3FontScale() {
        Configuration config = AccessibilityHelper.buildConfig(context, true);
        assertEquals(1.3f, config.fontScale, 0.001f);
    }

    @Test
    public void buildConfig_largeTextFalse_sets1_0FontScale() {
        Configuration config = AccessibilityHelper.buildConfig(context, false);
        assertEquals(1.0f, config.fontScale, 0.001f);
    }

    // ── joinButtonColor ───────────────────────────────────────────────────────

    @Test
    public void joinButtonColor_normalMode_isOrange() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        helper.setColorBlindMode(false);
        int color = helper.joinButtonColor();
        assertEquals(android.graphics.Color.parseColor("#F97316"), color);
    }

    @Test
    public void joinButtonColor_colorBlindMode_isBlue() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        helper.setColorBlindMode(true);
        int color = helper.joinButtonColor();
        assertEquals(android.graphics.Color.parseColor("#2563EB"), color);
    }

    // ── selectedStatusColor ───────────────────────────────────────────────────

    @Test
    public void selectedStatusColor_normalMode_isGreen() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        helper.setColorBlindMode(false);
        int color = helper.selectedStatusColor();
        assertEquals(android.graphics.Color.parseColor("#22C55E"), color);
    }

    @Test
    public void selectedStatusColor_colorBlindMode_isYellow() {
        AccessibilityHelper helper = new AccessibilityHelper(context);
        helper.setColorBlindMode(true);
        int color = helper.selectedStatusColor();
        assertEquals(android.graphics.Color.parseColor("#EAB308"), color);
    }
}
