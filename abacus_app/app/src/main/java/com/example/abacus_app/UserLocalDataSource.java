package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * The only class that directly reads and writes to {@link SharedPreferences}
 * for user-related persistence.
 *
 * <p>Responsible exclusively for persisting the device UUID locally so it
 * survives app restarts. Returns {@code null} from {@link #getDeviceId()} when
 * no UUID has been saved yet (first launch or after {@link #clearDeviceId()}).
 *
 * <p>Storage is backed by {@link EncryptedSharedPreferences} (AES-256-GCM keys,
 * AES-256-SIV values) to satisfy OWASP M2 — Insecure Data Storage. If the
 * Android Keystore is unavailable (e.g., certain emulators), the constructor
 * falls back to plain {@code SharedPreferences} so the app does not crash.
 *
 * <p>Architecture layer: Local Data Source<br>
 * Used by: {@link UserRepository}
 *
 * @see UserRepository
 * Ref: US 01.07.01
 */
public class UserLocalDataSource {

    private static final String TAG        = "UserLocalDataSource";
    static final String         PREFS_NAME = "user_prefs";
    static final String         KEY_UUID   = "device_uuid";

    private final SharedPreferences prefs;
    private final Context context;

    public UserLocalDataSource(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = createEncryptedPrefs(this.context);
    }

    /**
     * Creates an {@link EncryptedSharedPreferences} instance backed by the
     * Android Keystore (AES-256-GCM master key). Falls back to plain
     * {@link SharedPreferences} if the Keystore is unavailable.
     */
    private static SharedPreferences createEncryptedPrefs(Context ctx) {
        try {
            MasterKey masterKey = new MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            return EncryptedSharedPreferences.create(
                    ctx,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e);
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
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
     * Returns a stable device ID (ANDROID_ID) that persists across app reinstalls.
     * May return null on some emulators — callers should fall back to a random UUID.
     */
    public String getStableDeviceID() {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }
}
