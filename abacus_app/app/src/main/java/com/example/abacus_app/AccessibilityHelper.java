package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * AccessibilityHelper
 *
 * Manages accessibility preferences persisted in SharedPreferences.
 * Settings:
 *   - textScale: app-wide font scale (0.8–1.6, default 1.0)
 *   - oneHandedMode: optimized single-hand navigation
 */
public class AccessibilityHelper {

    private static final String PREFS_NAME = "accessibility_prefs";
    private static final String KEY_TEXT_SCALE       = "textScale";
    private static final String KEY_ONE_HANDED_MODE  = "oneHandedMode";

    private final SharedPreferences prefs;

    public AccessibilityHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Text scale ---

    public float getTextScale() {
        return prefs.getFloat(KEY_TEXT_SCALE, 1.0f);
    }

    public void setTextScale(float scale) {
        prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply();
    }

    // --- One-handed mode ---

    public boolean isOneHandedMode() {
        return prefs.getBoolean(KEY_ONE_HANDED_MODE, false);
    }

    public void setOneHandedMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_ONE_HANDED_MODE, enabled).apply();
    }

    /** Set before calling recreate() so MainActivity can navigate back to the accessibility screen. */
    public void setReturnToAccessibility(boolean value) {
        prefs.edit().putBoolean("returnToAccessibility", value).apply();
    }

    public boolean shouldReturnToAccessibility() {
        return prefs.getBoolean("returnToAccessibility", false);
    }

    public void clearReturnToAccessibility() {
        prefs.edit().remove("returnToAccessibility").apply();
    }

    /**
     * Returns a Configuration with font scale from the stored textScale value.
     * Pass to context.createConfigurationContext() in Application.attachBaseContext.
     */
    public static Configuration buildConfig(Context base, float textScale) {
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.fontScale = textScale;
        return config;
    }

}
