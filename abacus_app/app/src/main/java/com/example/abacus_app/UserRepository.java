package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

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
 * local database storage and Firebase Firestore.
 *
 * Used by: ProfileFragment, ProfileViewModel, MainActivity
 */
public class UserRepository {

    private final UserLocalDataSource  localDataSource;
    private final UserRemoteDataSource remoteDataSource;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public UserRepository(UserLocalDataSource localDataSource,
                          UserRemoteDataSource remoteDataSource) {
        this.localDataSource  = localDataSource;
        this.remoteDataSource = remoteDataSource;
    }

    /** Returns existing UUID or creates and persists a new one. */
    private String getOrCreateUUID() {
        String uuid = localDataSource.getUUIDSync();
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
            localDataSource.saveUUIDSync(uuid);
        }
        return uuid;
    }

    /**
     * Initializes the user: ensures a UUID exists, signs in anonymously with
     * Firebase Auth, and creates a Firestore document if one doesn't exist yet.
     * This is the main entry point that should be called on app launch.
     */
    public void initializeUserAsync() {
        executor.submit(() -> {
            try {
                String uuid = getOrCreateUUID();
                FirebaseAuth auth = FirebaseAuth.getInstance();

                // Check if user is already authenticated
                if (auth.getCurrentUser() != null) {
                    // User is already authenticated (either anonymous or with email/password)
                    // Just ensure Firestore document exists
                    ensureFirestoreDocumentExists(uuid);
                } else {
                    // No authenticated user, sign in anonymously
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
     * Ensures that a Firestore document exists for the given UUID.
     * Creates a new document if one doesn't exist.
     */
    private void ensureFirestoreDocumentExists(String uuid) {
        executor.submit(() -> {
            try {
                User existing = remoteDataSource.getUserSync(uuid);
                if (existing == null) {
                    // Create user data as a Map<String, Object>
                    java.util.Map<String, Object> userData = new java.util.HashMap<>();
                    userData.put("uid", uuid);
                    userData.put("email", "");
                    userData.put("name", "New User");
                    userData.put("createdAt", Timestamp.now().toString());

                    remoteDataSource.createUserSync(uuid, userData);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls initializeUserAsync() internally.
     */
    public void initializeUser() {
        initializeUserAsync();
    }

    /**
     * Retrieves the current device UUID and returns it via callback on the main thread.
     * This is the implementation for getCurrentUserId() from specification.
     */
    public void getCurrentUserIdAsync(StringCallback callback) {
        executor.submit(() -> {
            String uuid = localDataSource.getUUIDSync();
            mainHandler.post(() -> callback.onResult(uuid));
        });
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls getCurrentUserIdAsync() internally.
     */
    public void getCurrentUserId(StringCallback callback) {
        getCurrentUserIdAsync(callback);
    }

    /**
     * Fetches the user profile from Firestore and returns it via callback on the main thread.
     * This is the implementation for getProfile() from specification.
     */
    public void getProfileAsync(UserCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = localDataSource.getUUIDSync();
                android.util.Log.d("UserRepository", "Getting profile for UUID: " + uuid);
                
                if (uuid == null) {
                    android.util.Log.w("UserRepository", "No UUID found, returning null");
                    mainHandler.post(() -> callback.onResult(null));
                    return;
                }
                
                User user = remoteDataSource.getUserSync(uuid);
                if (user != null) {
                    android.util.Log.d("UserRepository", "Retrieved user: " + user.getName() + 
                        " (" + user.getEmail() + "), isGuest: " + user.isGuest());
                } else {
                    android.util.Log.w("UserRepository", "No user document found for UUID: " + uuid);
                }
                
                mainHandler.post(() -> callback.onResult(user));
            } catch (Exception e) {
                android.util.Log.e("UserRepository", "Error getting profile", e);
                e.printStackTrace();
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls getProfileAsync() internally.
     */
    public void getProfile(UserCallback callback) {
        getProfileAsync(callback);
    }

    /**
     * Saves profile fields to Firestore for the current user.
     * This is the implementation for saveProfile() from specification.
     *
     * @param profileData Map of field names → values to update.
     * @param callback    Called on the main thread when done (null on success, exception on error).
     */
    public void saveProfileAsync(Map<String, Object> profileData, VoidCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = localDataSource.getUUIDSync();
                android.util.Log.d("UserRepository", "Saving profile for UUID: " + uuid + 
                    ", data: " + profileData.toString());
                
                if (uuid != null) {
                    remoteDataSource.updateUserSync(uuid, profileData);
                    android.util.Log.d("UserRepository", "Profile saved successfully");
                } else {
                    android.util.Log.w("UserRepository", "No UUID found, cannot save profile");
                }
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                android.util.Log.e("UserRepository", "Error saving profile", e);
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls saveProfileAsync() internally.
     */
    public void saveProfile(Map<String, Object> profileData, VoidCallback callback) {
        saveProfileAsync(profileData, callback);
    }

    /**
     * Soft-deletes the current user's profile in Firestore.
     * This is the implementation for deleteProfile() from specification.
     *
     * @param callback Called on the main thread when done (null on success, exception on error).
     */
    public void deleteProfileAsync(VoidCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = localDataSource.getUUIDSync();
                if (uuid != null) {
                    remoteDataSource.deleteUserSync(uuid);
                }
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Alternative method name from specification for consistency.
     * Calls deleteProfileAsync() internally.
     */
    public void deleteProfile(VoidCallback callback) {
        deleteProfileAsync(callback);
    }


    public interface UserCallback {
        void onResult(User user);
    }

    public interface StringCallback {
        void onResult(String value);
    }

    public interface VoidCallback {
        /** @param error null on success, non-null if something went wrong. */
        void onComplete(Exception error);
    }
}