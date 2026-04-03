package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Architecture Layer: Repository
 *
 * Coordinates user initialization and synchronization between
 * local SharedPreferences storage and Firebase Firestore.
 *
 * Uses a stable device ID (ANDROID_ID) as the primary identity,
 * ensuring that user data (like notifications) survives app re-installs.
 */
public class UserRepository {

    private final UserLocalDataSource  localDataSource;
    private final UserRemoteDataSource remoteDataSource;

    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public UserRepository(UserLocalDataSource localDataSource,
                          UserRemoteDataSource remoteDataSource) {
        this.localDataSource  = localDataSource;
        this.remoteDataSource = remoteDataSource;
    }

    /**
     * Resolves the device's identity.
     * Uses the stable ANDROID_ID as the UUID if no identity exists in local storage.
     */
    private String getOrCreateUUID() {
        String uuid = localDataSource.getUUIDSync();
        if (uuid == null) {
            // Prefer stable ANDROID_ID so identity survives reinstalls;
            // fall back to random UUID if ANDROID_ID is unavailable (some emulators).
            uuid = localDataSource.getStableDeviceID();
            if (uuid == null) uuid = UUID.randomUUID().toString();
            localDataSource.saveUUIDSync(uuid);
        }
        return uuid;
    }

    /**
     * Resolves or creates the device identity, signs in anonymously via Firebase Auth,
     * and ensures a Firestore user document exists for this device.
     *
     * <p>On first launch the stable {@code ANDROID_ID} is used as the UUID; a random
     * UUID is generated as a fallback if {@code ANDROID_ID} is unavailable.
     * On subsequent launches the previously stored UUID is reused.
     *
     * <p>Runs entirely on the background executor — safe to call from any thread.
     * Ref: US 01.07.01
     */
    public void initializeUserAsync() {
        executor.submit(() -> {
            try {
                String uuid = getOrCreateUUID();
                FirebaseAuth auth = FirebaseAuth.getInstance();

                if (auth.getCurrentUser() != null) {
                    ensureFirestoreDocumentExists(uuid);
                } else {
                    auth.signInAnonymously()
                            .addOnCompleteListener(task -> {
                                if (task.isSuccessful()) {
                                    ensureFirestoreDocumentExists(uuid);
                                }
                            });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Creates a default {@code users/{uuid}} document in Firestore if one does not
     * already exist. Silently no-ops if the document is present, preventing data loss
     * on re-install when the same UUID is recovered from {@code ANDROID_ID}.
     *
     * <p>Default fields written on first creation: {@code deviceId}, {@code email},
     * {@code name} ("New User"), {@code createdAt}, {@code role} ("entrant"),
     * {@code notificationsEnabled} (true), {@code isGuest} (true), {@code isDeleted} (false).
     *
     * @param uuid the stable device UUID that acts as the document key
     */
    private void ensureFirestoreDocumentExists(String uuid) {
        executor.submit(() -> {
            try {
                User existing = remoteDataSource.getUserSync(uuid);
                if (existing == null) {
                    java.util.Map<String, Object> userData = new java.util.HashMap<>();
                    userData.put("deviceId", uuid);
                    userData.put("email",    "");
                    userData.put("name",     "New User");
                    userData.put("createdAt", Timestamp.now());
                    userData.put("role",      "entrant");
                    userData.put("notificationsEnabled", true);
                    userData.put("isGuest",  true);
                    userData.put("isDeleted", false);
                    remoteDataSource.createUserSync(uuid, userData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Convenience alias for {@link #initializeUserAsync()}.
     * Provided for call-sites that prefer the spec-named method.
     * Ref: US 01.07.01
     */
    public void initializeUser() {
        initializeUserAsync();
    }

    /**
     * Returns the current device UUID via {@code callback}, generating and persisting
     * a new one if none exists yet.
     *
     * <p>The callback is always delivered on the main thread.
     *
     * @param callback receives the UUID string; never null after this call
     * Ref: US 01.07.01
     */
    public void getCurrentUserIdAsync(StringCallback callback) {
        executor.submit(() -> {
            String uuid = getOrCreateUUID();
            mainHandler.post(() -> callback.onResult(uuid));
        });
    }

    /**
     * Convenience alias for {@link #getCurrentUserIdAsync(StringCallback)}.
     * Ref: US 01.07.01
     */
    public void getCurrentUserId(StringCallback callback) {
        getCurrentUserIdAsync(callback);
    }

    /**
     * Fetches the Firestore user document for the current authenticated account.
     * Uses Firebase UID for authenticated users, device UUID for guests.
     *
     * <p>Returns {@code null} to the callback if no document exists or if an error
     * occurs. The callback is always delivered on the main thread.
     *
     * @param callback receives the {@link User} object, or {@code null} on failure
     * Ref: US 01.02.02
     */
    public void getProfileAsync(UserCallback callback) {
        executor.submit(() -> {
            try {
                // For authenticated users, use Firebase UID (unique per account)
                // For guests, fall back to device UUID
                String firebaseUser = FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                String docId = (firebaseUser != null && !firebaseUser.isEmpty()) ?
                        firebaseUser : getOrCreateUUID();

                User user = remoteDataSource.getUserSync(docId);


                if (user != null && user.isDeleted()) {
                    // The user is soft-deleted. Sign them out and clear session.
                    clearLocalSession();

                    // Return null to the UI so it behaves as if no user exists/is logged in
                    mainHandler.post(() -> callback.onResult(null));
                    return;
                }

                mainHandler.post(() -> callback.onResult(user));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Convenience alias for {@link #getProfileAsync(UserCallback)}.
     * Ref: US 01.02.02
     */
    public void getProfile(UserCallback callback) {
        getProfileAsync(callback);
    }

    /**
     * Merges {@code profileData} into the Firestore user document for the current
     * authenticated account using {@code SetOptions.merge()}, preserving fields not in the map.
     * Uses Firebase UID for authenticated users, device UUID for guests.
     *
     * <p>The callback receives {@code null} on success or the exception on failure.
     * Always delivered on the main thread.
     *
     * @param profileData map of field names to values to write (e.g. "name", "email")
     * @param callback    receives {@code null} on success or the thrown exception
     * Ref: US 01.02.01, US 01.02.02
     */
    public void saveProfileAsync(Map<String, Object> profileData, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // For authenticated users, use Firebase UID (unique per account)
                // For guests, fall back to device UUID
                String firebaseUser = FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                String docId = (firebaseUser != null && !firebaseUser.isEmpty()) ?
                        firebaseUser : getOrCreateUUID();

                remoteDataSource.updateUserSync(docId, profileData);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Convenience alias for {@link #saveProfileAsync(Map, VoidCallback)}.
     * Ref: US 01.02.01
     */
    public void saveProfile(Map<String, Object> profileData, VoidCallback callback) {
        saveProfileAsync(profileData, callback);
    }

    /**
     * Wraps {@code preferences} under the {@code "preferences"} key and merges
     * it into the Firestore user document. Other top-level fields are not affected.
     *
     * @param preferences map of preference keys to values (e.g. "categories", "locationRangeKm")
     * @param callback    receives {@code null} on success or the thrown exception
     */
    public void savePreferencesAsync(Map<String, Object> preferences, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // For authenticated users, use Firebase UID (unique per account)
                // For guests, fall back to device UUID
                String firebaseUser = FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                String docId = (firebaseUser != null && !firebaseUser.isEmpty()) ?
                        firebaseUser : getOrCreateUUID();

                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("preferences", preferences);
                remoteDataSource.updateUserSync(docId, data);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Soft-deletes the Firestore user document, hard-deletes the Firebase Auth account,
     * removes all waitlist entries in the flat {@code registrations/} collection, and
     * clears the locally stored UUID so the next launch creates a fresh identity.
     *
     * <p>The callback receives {@code null} on success. On partial failure (e.g.
     * Auth delete fails) the exception is delivered via the callback and the local
     * UUID is NOT cleared, preventing the user from being locked out.
     *
     * @param callback receives {@code null} on success or the thrown exception
     * Ref: US 01.02.04
     */
    public void deleteProfileAsync(VoidCallback callback) {
        executor.submit(() -> {
            try {
                // Delete both the authenticated profile (if signed in) and device profile
                String firebaseUid = FirebaseAuth.getInstance().getCurrentUser() != null ?
                        FirebaseAuth.getInstance().getCurrentUser().getUid() : null;

                if (firebaseUid != null) {
                    // Delete authenticated account profile
                    remoteDataSource.deleteUserSync(firebaseUid);
                    remoteDataSource.deleteWaitlistEntriesForUser(firebaseUid);
                }

                String deviceUuid = localDataSource.getUUIDSync();
                if (deviceUuid != null) {
                    // Also delete guest/device profile if it exists
                    remoteDataSource.deleteUserSync(deviceUuid);
                    remoteDataSource.deleteWaitlistEntriesForUser(deviceUuid);
                }

                com.google.firebase.auth.FirebaseUser authUser =
                        FirebaseAuth.getInstance().getCurrentUser();
                if (authUser != null) {
                    Tasks.await(authUser.delete());
                }

                localDataSource.clearDeviceId();

                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Convenience alias for {@link #deleteProfileAsync(VoidCallback)}.
     * Ref: US 01.02.04
     */
    public void deleteProfile(VoidCallback callback) {
        deleteProfileAsync(callback);
    }

    /**
     * Shuts down the background executor. Call from the owning lifecycle component's
     * onDestroy() / onTerminate() to prevent thread leaks.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Clears the locally stored UUID and signs out of Firebase Auth.
     * After this call the device has no identity; the next call to
     * initializeUser() will generate a fresh UUID and anonymous session.
     */
    public void clearLocalSession() {
        localDataSource.clearDeviceId();
        FirebaseAuth.getInstance().signOut();
    }

    /** Callback for operations that return a {@link User} object. */
    public interface UserCallback {
        void onResult(User user);
    }

    /** Callback for operations that return a {@link String} value (e.g. a UUID). */
    public interface StringCallback {
        void onResult(String value);
    }

    /**
     * Callback for void operations.
     * {@code error} is {@code null} on success; non-null if the operation failed.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
