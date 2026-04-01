package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * The only class that directly reads and writes to {@link SharedPreferences}
 * for user-related persistence.
 *
 * <p>Responsible exclusively for persisting the device UUID locally so it
 * survives app restarts. Returns {@code null} from {@link #getDeviceId()} when
 * no UUID has been saved yet (first launch or after {@link #clearDeviceId()}).
 *
 * <p>Architecture layer: Local Data Source<br>
 * Used by: {@link UserRepository}
 *
 * @see UserRepository
 * Ref: US 01.07.01
 */
public class UserLocalDataSource {

    static final String PREFS_NAME = "user_prefs";
    static final String KEY_UUID   = "device_uuid";

    private final SharedPreferences prefs;

    private final Context context;

    public UserLocalDataSource(Context context) {
        this.context = context.getApplicationContext(); // safe for long-lived use
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

    /**
     * Returns a stable device ID that persists across app reinstalls.
     * Only used if no UUID has been saved yet.
     */
    public String getStableDeviceID() {
        return Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
    }

}