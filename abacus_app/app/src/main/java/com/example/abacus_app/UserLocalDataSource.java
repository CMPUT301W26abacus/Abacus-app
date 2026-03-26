package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * Architecture Layer: Local Data Source
 *
 * Stores and retrieves user-related data using SharedPreferences.
 * Provides a stable device identifier to persist identity across re-installs.
 */
public class UserLocalDataSource {

    static final String PREFS_NAME = "user_prefs";
    static final String KEY_UUID   = "device_uuid";

    private final SharedPreferences prefs;
    private final Context context;

    public UserLocalDataSource(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Returns a stable identifier for this device that survives app re-installs.
     * Uses ANDROID_ID.
     */
    public String getStableDeviceID() {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return (androidId != null) ? androidId : "fallback_id";
    }

    public String getUUIDSync() {
        return prefs.getString(KEY_UUID, null);
    }

    public String getDeviceId() {
        return getUUIDSync();
    }

    public void saveUUIDSync(String uuid) {
        prefs.edit().putString(KEY_UUID, uuid).apply();
    }

    public void saveDeviceId(String uuid) {
        saveUUIDSync(uuid);
    }

    public void clearDeviceId() {
        prefs.edit().remove(KEY_UUID).apply();
    }
}
