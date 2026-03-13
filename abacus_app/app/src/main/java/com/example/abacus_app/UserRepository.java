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
 * local SharedPreferences storage and Firebase Firestore.
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

    private String getOrCreateUUID() {
        String uuid = localDataSource.getUUIDSync();
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
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
                    userData.put("uid", uuid);
                    userData.put("email", "");
                    userData.put("name", "New User");
                    userData.put("createdAt", Timestamp.now().toString());
                    
                    // Added default fields
                    userData.put("role", "entrant");
                    userData.put("notificationsEnabled", true);
                    userData.put("isGuest", true);
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
            String uuid = localDataSource.getUUIDSync();
            mainHandler.post(() -> callback.onResult(uuid));
        });
    }

    public void getCurrentUserId(StringCallback callback) {
        getCurrentUserIdAsync(callback);
    }

    public void getProfileAsync(UserCallback callback) {
        executor.submit(() -> {
            try {
                String uuid = localDataSource.getUUIDSync();
                if (uuid == null) {
                    mainHandler.post(() -> callback.onResult(null));
                    return;
                }
                
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
                String uuid = localDataSource.getUUIDSync();
                if (uuid != null) {
                    remoteDataSource.updateUserSync(uuid, profileData);
                }
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
        void onComplete(Exception error);
    }
}
