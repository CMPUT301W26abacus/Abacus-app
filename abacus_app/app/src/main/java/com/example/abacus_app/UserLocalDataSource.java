package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Architecture Layer: Local Data Source
 *
 * Stores and retrieves user-related data using SharedPreferences.
 * Replaces the Kotlin DataStore implementation with a pure Java equivalent.
 *
 * Used by: UserRepository
 */
public class UserLocalDataSource {

    static final String PREFS_NAME = "user_prefs";
    static final String KEY_UUID   = "device_uuid";

    private final SharedPreferences prefs;

    public UserLocalDataSource(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Retrieves the device UUID from SharedPreferences.
     * This is the implementation for getDeviceId() from specification.
     *
     * @return The device UUID, or null if not yet set.
     */
    public String getUUIDSync() {
        return prefs.getString(KEY_UUID, null);
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls getUUIDSync() internally.
     */
    public String getDeviceId() {
        return getUUIDSync();
    }

    /**
     * Saves the device UUID to SharedPreferences.
     * This is the implementation for saveDeviceId() from specification.
     *
     * @param uuid The UUID to persist.
     */
    public void saveUUIDSync(String uuid) {
        prefs.edit().putString(KEY_UUID, uuid).apply();
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls saveUUIDSync() internally.
     */
    public void saveDeviceId(String uuid) {
        saveUUIDSync(uuid);
    }

    /**
     * Removes the stored UUID from SharedPreferences.
     * Called on logout so the next launch generates a fresh identity.
     */
    public void clearDeviceId() {
        prefs.edit().remove(KEY_UUID).apply();
    }
}