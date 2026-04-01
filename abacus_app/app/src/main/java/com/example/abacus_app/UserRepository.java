package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;
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
            // Use stable device ID instead of random UUID to persist identity across reinstalls
            uuid = localDataSource.getStableDeviceID();
            localDataSource.saveUUIDSync(uuid);
        }
        return uuid;
    }

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

    public void initializeUser() {
        initializeUserAsync();
    }

    public void getCurrentUserIdAsync(StringCallback callback) {
        executor.submit(() -> {
            String uuid = getOrCreateUUID();
            mainHandler.post(() -> callback.onResult(uuid));
        });
    }

    public void getCurrentUserId(StringCallback callback) {
        getCurrentUserIdAsync(callback);
    }

    public void getProfileAsync(UserCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = getOrCreateUUID();
                User user = remoteDataSource.getUserSync(uuid);
                mainHandler.post(() -> callback.onResult(user));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    public void getProfile(UserCallback callback) {
        getProfileAsync(callback);
    }

    public void saveProfileAsync(Map<String, Object> profileData, VoidCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = getOrCreateUUID();
                remoteDataSource.updateUserSync(uuid, profileData);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    public void saveProfile(Map<String, Object> profileData, VoidCallback callback) {
        saveProfileAsync(profileData, callback);
    }

    public void savePreferencesAsync(Map<String, Object> preferences, VoidCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = getOrCreateUUID();
                java.util.Map<String, Object> data = new java.util.HashMap<>();
                data.put("preferences", preferences);
                remoteDataSource.updateUserSync(uuid, data);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    public void deleteProfileAsync(VoidCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = localDataSource.getUUIDSync();
                if (uuid != null) {
                    remoteDataSource.deleteUserSync(uuid);
                    remoteDataSource.deleteWaitlistEntriesForUser(uuid);
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

    public void deleteProfile(VoidCallback callback) {
        deleteProfileAsync(callback);
    }

    public void clearLocalSession() {
        localDataSource.clearDeviceId();
        FirebaseAuth.getInstance().signOut();
    }

    public interface UserCallback {
        void onResult(User user);
    }

    public interface StringCallback {
        void onResult(String value);
    }

    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
