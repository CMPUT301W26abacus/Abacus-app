package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * AccessibilityHelper
 *
 * Manages accessibility preferences persisted in SharedPreferences.
 * Two settings are supported:
 *   - colorBlindMode: replaces red/green UI colours with blue/yellow safe alternatives.
 *   - largeText: scales the app font to 1.3× normal size.
 *
 * Font scale is applied at the Application level via {@code attachBaseContext}.
 * Color-blind theming is applied at runtime by activities/adapters.
 */
public class AccessibilityHelper {

    private static final String PREFS_NAME      = "accessibility_prefs";
    private static final String KEY_COLOR_BLIND = "colorBlindMode";
    private static final String KEY_LARGE_TEXT  = "largeText";

    private final SharedPreferences prefs;

    public AccessibilityHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isColorBlindMode() {
        return prefs.getBoolean(KEY_COLOR_BLIND, false);
    }

    public boolean isLargeText() {
        return prefs.getBoolean(KEY_LARGE_TEXT, false);
    }

    public void setColorBlindMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_COLOR_BLIND, enabled).apply();
    }

    public void setLargeText(boolean enabled) {
        prefs.edit().putBoolean(KEY_LARGE_TEXT, enabled).apply();
    }

    /**
     * Returns a {@link Configuration} with font scale set based on {@code largeText}.
     * Pass this to {@code context.createConfigurationContext()} in
     * {@code Application.attachBaseContext} so the scale applies globally.
     *
     * @param base      the base context (from Application.attachBaseContext)
     * @param largeText whether large text scaling is enabled
     * @return a Configuration with the appropriate fontScale
     */
    public static Configuration buildConfig(Context base, boolean largeText) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = largeText ? 1.3f : 1.0f;
        return config;
    }

    /**
     * Returns the "join button" colour appropriate for the current accessibility mode.
     * Default: orange (#F97316). Color-blind safe: blue (#2563EB).
     */
    public int joinButtonColor() {
        return isColorBlindMode()
                ? android.graphics.Color.parseColor("#2563EB")
                : android.graphics.Color.parseColor("#F97316");
    }

    /**
     * Returns the "selected/winner" status colour appropriate for the current mode.
     * Default: green. Color-blind safe: yellow (#EAB308).
     */
    public int selectedStatusColor() {
        return isColorBlindMode()
                ? android.graphics.Color.parseColor("#EAB308")
                : android.graphics.Color.parseColor("#22C55E");
    }
}
