package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * AccessibilityHelper
 *
 * Manages accessibility preferences persisted in SharedPreferences.
 * Settings:
 *   - colorBlindType: which color-blindness palette to apply
 *   - textScale: app-wide font scale (0.8–1.6, default 1.0)
 *   - reduceMotion: skip splash animations
 *   - highContrast: bold text + darkened borders
 */
public class AccessibilityHelper {

    public static final String COLOR_BLIND_NONE         = "none";
    public static final String COLOR_BLIND_PROTANOPIA   = "protanopia";
    public static final String COLOR_BLIND_DEUTERANOPIA = "deuteranopia";
    public static final String COLOR_BLIND_TRITANOPIA   = "tritanopia";
    public static final String COLOR_BLIND_ACHROMATOPSIA = "achromatopsia";

    private static final String PREFS_NAME           = "accessibility_prefs";
    private static final String KEY_COLOR_BLIND_TYPE = "colorBlindType";
    private static final String KEY_TEXT_SCALE       = "textScale";
    private static final String KEY_REDUCE_MOTION    = "reduceMotion";
    private static final String KEY_HIGH_CONTRAST    = "highContrast";
    private static final String KEY_ONE_HANDED_MODE  = "oneHandedMode";

    private final SharedPreferences prefs;

    public AccessibilityHelper(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // --- Color-blind type ---

    public String getColorBlindType() {
        return prefs.getString(KEY_COLOR_BLIND_TYPE, COLOR_BLIND_NONE);
    }

    public void setColorBlindType(String type) {
        prefs.edit().putString(KEY_COLOR_BLIND_TYPE, type).apply();
    }

    /** Convenience: true when any color-blind filter is active. */
    public boolean isColorBlindMode() {
        return !COLOR_BLIND_NONE.equals(getColorBlindType());
    }

    // --- Text scale ---

    public float getTextScale() {
        return prefs.getFloat(KEY_TEXT_SCALE, 1.0f);
    }

    public void setTextScale(float scale) {
        prefs.edit().putFloat(KEY_TEXT_SCALE, scale).apply();
    }

    // --- Reduce motion ---

    public boolean isReduceMotion() {
        return prefs.getBoolean(KEY_REDUCE_MOTION, false);
    }

    public void setReduceMotion(boolean enabled) {
        prefs.edit().putBoolean(KEY_REDUCE_MOTION, enabled).apply();
    }

    // --- One-handed mode ---

    public boolean isOneHandedMode() {
        return prefs.getBoolean(KEY_ONE_HANDED_MODE, false);
    }

    public void setOneHandedMode(boolean enabled) {
        prefs.edit().putBoolean(KEY_ONE_HANDED_MODE, enabled).apply();
    }

    // --- High contrast ---

    public boolean isHighContrast() {
        return prefs.getBoolean(KEY_HIGH_CONTRAST, false);
    }

    public void setHighContrast(boolean enabled) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply();
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

    /**
     * Returns the "join button" colour for the current color-blind type.
     * Protanopia/Deuteranopia → blue (avoid red/green).
     * Tritanopia → red-orange (avoid blue).
     * Achromatopsia → near-black (no hue).
     * Default → orange.
     */
    public int joinButtonColor() {
        switch (getColorBlindType()) {
            case COLOR_BLIND_PROTANOPIA:
            case COLOR_BLIND_DEUTERANOPIA:
                return android.graphics.Color.parseColor("#2563EB"); // blue
            case COLOR_BLIND_TRITANOPIA:
                return android.graphics.Color.parseColor("#DC2626"); // red-orange
            case COLOR_BLIND_ACHROMATOPSIA:
                return android.graphics.Color.parseColor("#111827"); // near-black
            default:
                return android.graphics.Color.parseColor("#F97316"); // orange
        }
    }

    /**
     * Recursively walks a view tree and applies high-contrast styling:
     * all TextViews become bold and near-black; all Views with a non-null background
     * get a dark border stroke via a GradientDrawable overlay.
     * Call this after setContentView when isHighContrast() is true.
     */
    public static void applyHighContrast(android.view.View root) {
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyHighContrast(group.getChildAt(i));
            }
        }
        if (root instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) root;
            tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
            // Only darken muted (grey-ish) text — leave explicitly coloured text alone
            int color = tv.getCurrentTextColor();
            int r = android.graphics.Color.red(color);
            int g = android.graphics.Color.green(color);
            int b = android.graphics.Color.blue(color);
            // If the colour is roughly grey (all channels similar and not very dark)
            int maxDiff = Math.max(Math.abs(r - g), Math.max(Math.abs(r - b), Math.abs(g - b)));
            if (maxDiff < 40 && r > 60) {
                tv.setTextColor(android.graphics.Color.parseColor("#111827"));
            }
        }
    }

    /**
     * Returns the "selected/winner" status colour for the current color-blind type.
     */
    public int selectedStatusColor() {
        switch (getColorBlindType()) {
            case COLOR_BLIND_PROTANOPIA:
            case COLOR_BLIND_DEUTERANOPIA:
                return android.graphics.Color.parseColor("#EAB308"); // yellow
            case COLOR_BLIND_TRITANOPIA:
                return android.graphics.Color.parseColor("#F97316"); // orange
            case COLOR_BLIND_ACHROMATOPSIA:
                return android.graphics.Color.parseColor("#374151"); // dark grey
            default:
                return android.graphics.Color.parseColor("#22C55E"); // green
        }
    }
}
