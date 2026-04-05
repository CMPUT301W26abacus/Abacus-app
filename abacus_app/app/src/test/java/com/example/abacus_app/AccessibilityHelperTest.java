package com.example.abacus_app;

import android.content.Context;
import android.content.res.Configuration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

/**
 * Unit tests for the {@link AccessibilityHelper} class.
 * Tests accessibility features including text scaling and one-handed mode.
 */
@RunWith(RobolectricTestRunner.class)
public class AccessibilityHelperTest {

    private Context context;
    private AccessibilityHelper accessibilityHelper;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.getApplication();
        accessibilityHelper = new AccessibilityHelper(context);
    }

    @Test
    public void testAccessibilityHelperInstantiation() {
        assertNotNull("AccessibilityHelper should be instantiated", accessibilityHelper);
    }

    @Test
    public void testGetTextScaleDefault() {
        float defaultScale = accessibilityHelper.getTextScale();
        assertEquals("Default text scale should be 1.0f", 1.0f, defaultScale, 0.01f);
    }

    @Test
    public void testSetAndGetTextScale() {
        float testScale = 1.5f;
        accessibilityHelper.setTextScale(testScale);
        float retrievedScale = accessibilityHelper.getTextScale();
        assertEquals("Text scale should be persisted and retrieved", testScale, retrievedScale, 0.01f);
    }

    @Test
    public void testBuildConfigReturnsConfiguration() {
        // buildConfig requires both context and float textScale
        Configuration config = AccessibilityHelper.buildConfig(context, 1.0f);
        assertNotNull("buildConfig should return a non-null Configuration", config);
    }

    @Test
    public void testBuildConfigAppliesTextScale() {
        float testScale = 1.5f;
        Configuration config = AccessibilityHelper.buildConfig(context, testScale);
        assertEquals("Configuration should apply the specified text scale", testScale, config.fontScale, 0.01f);
    }

    @Test
    public void testBuildConfigWithDifferentScales() {
        float[] testScales = {0.8f, 1.0f, 1.6f};
        for (float scale : testScales) {
            Configuration config = AccessibilityHelper.buildConfig(context, scale);
            assertEquals("fontScale should match the provided scale", scale, config.fontScale, 0.01f);
        }
    }

    @Test
    public void testGetOneHandedModeDefault() {
        boolean oneHandedMode = accessibilityHelper.isOneHandedMode();
        assertFalse("One-handed mode should be disabled by default", oneHandedMode);
    }

    @Test
    public void testSetAndGetOneHandedMode() {
        accessibilityHelper.setOneHandedMode(true);
        assertTrue("One-handed mode should be enabled after setting", accessibilityHelper.isOneHandedMode());
        accessibilityHelper.setOneHandedMode(false);
        assertFalse("One-handed mode should be disabled after setting", accessibilityHelper.isOneHandedMode());
    }

    @Test
    public void testReturnToAccessibilityFlag() {
        assertFalse("Return flag should be false by default", accessibilityHelper.shouldReturnToAccessibility());
        accessibilityHelper.setReturnToAccessibility(true);
        assertTrue("Return flag should be true after setting", accessibilityHelper.shouldReturnToAccessibility());
        accessibilityHelper.clearReturnToAccessibility();
        assertFalse("Return flag should be false after clearing", accessibilityHelper.shouldReturnToAccessibility());
    }
}
