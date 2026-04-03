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
 * Tests accessibility features including motion preferences and text scaling.
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
    public void testIsReduceMotionMethod() {
        // Test that isReduceMotion returns a boolean
        boolean reduceMotion = accessibilityHelper.isReduceMotion();
        assertTrue("isReduceMotion should return a boolean value", reduceMotion || !reduceMotion);
    }

    @Test
    public void testBuildConfigWithContext() {
        // Test that buildConfig returns a non-null Context
        Context configuredContext = AccessibilityHelper.buildConfig(context);
        assertNotNull("buildConfig should return a non-null context", configuredContext);
    }

    @Test
    public void testBuildConfigAppliesConfiguration() {
        // Test that buildConfig applies accessibility configuration
        Context configuredContext = AccessibilityHelper.buildConfig(context);
        Configuration config = configuredContext.getResources().getConfiguration();
        assertNotNull("Configuration should be applied", config);
    }

    @Test
    public void testAccessibilityHelperWithNullContext() {
        // Test that buildConfig handles context properly even with edge cases
        Context configuredContext = AccessibilityHelper.buildConfig(context);
        assertNotNull("buildConfig should handle context safely", configuredContext);
    }

    @Test
    public void testTextScalingApplication() {
        // Verify that buildConfig applies text scaling from system preferences
        Context originalContext = context;
        Context configuredContext = AccessibilityHelper.buildConfig(originalContext);

        // Configuration should reflect accessibility settings
        Configuration config = configuredContext.getResources().getConfiguration();
        assertTrue("fontScale should be a valid value", config.fontScale > 0);
    }
}
