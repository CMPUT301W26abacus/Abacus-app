package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.Map;

/**
 * The only class that directly touches the Firestore {@code users/} collection.
 *
 * <p>All Firestore reads and writes for user documents are routed through this
 * class. It exposes synchronous, blocking methods (suffixed {@code Sync}) that
 * must be called on a background thread, plus spec-named aliases that delegate
 * to those implementations.
 *
 * <p>Helper methods ({@link #getString}, {@link #getBoolean}, {@link #getLong})
 * provide null-safe field extraction so the caller never needs to cast raw
 * {@code Object} values returned by Firestore document snapshots.
 *
 * <p>Architecture layer: Remote Data Source<br>
 * Used by: {@link UserRepository}
 *
 * @see UserRepository
 * Ref: US 01.02.01–04
 */
public class UserRemoteDataSource {

    private static final String TAG        = "UserRemoteDataSource";
    private static final String COLLECTION = "users";

    private final FirebaseFirestore db;

    public interface UserCallback {
        void onCallback(User user);
    }

    public UserRemoteDataSource(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Creates a new document at {@code users/{uuid}} with the supplied field map.
     * Overwrites any existing document at that path.
     *
     * @param uuid the document ID (stable device UUID)
     * @param data field map to write (must include at minimum "deviceId", "createdAt")
     * @throws Exception if the Firestore write fails
     */
    public void createUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(db.collection(COLLECTION).document(uuid).set(data));
    }

    /**
     * Reads the {@code users/{uuid}} document asynchronously and delivers the parsed
     * {@link User} to {@code callback}. Calls {@code callback.onCallback(null)} if the
     * document does not exist or if an error occurs.
     *
     * @param uuid     the document ID to look up
     * @param callback receives the {@link User} object or {@code null}
     */
    public void getUser(String uuid, UserCallback callback) {
        db.collection(COLLECTION).document(uuid).get()
                .addOnSuccessListener(snap -> {
                    if (!snap.exists()) {
                        callback.onCallback(null);
                        return;
                    }
                    callback.onCallback(parseUser(snap));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user: " + uuid, e);
                    callback.onCallback(null);
                });
    }

    /**
     * Reads the {@code users/{uuid}} document synchronously (blocking).
     * Must be called on a background thread.
     *
     * @param uuid the document ID to look up
     * @return the parsed {@link User}, or {@code null} if the document does not exist
     * @throws Exception if the Firestore read fails
     */
    public User getUserSync(String uuid) throws Exception {
        DocumentSnapshot snap = Tasks.await(
                db.collection(COLLECTION).document(uuid).get());

        if (!snap.exists()) return null;

        return parseUser(snap);
    }

    private User parseUser(DocumentSnapshot snap) {
        User user = new User();
        user.setUid(snap.getId());

        user.setName(getString(snap, "name"));
        user.setEmail(getString(snap, "email"));
        user.setPhone(getString(snap, "phone"));
        user.setCreatedAt(getString(snap, "createdAt"));
        user.setDeleted(getBoolean(snap, "isDeleted"));
        user.setLastLoginAt(getString(snap, "lastLoginAt"));

        String role = getString(snap, "role");
        user.setRole((role == null || role.isEmpty()) ? "entrant" : role);

        user.setNotificationsEnabled(getBoolean(snap, "notificationsEnabled"));

        user.setProfilePhotoUrl(getString(snap, "profilePhotoUrl"));
        user.setVerificationStatus(getString(snap, "verificationStatus"));
        user.setPreferredLanguage(getString(snap, "preferredLanguage"));
        user.setTimezone(getString(snap, "timezone"));
        user.setBio(getString(snap, "bio"));
        user.setOrganizationName(getString(snap, "organizationName"));

        Object prefsRaw = snap.get("preferences");
        if (prefsRaw instanceof Map) {
            //noinspection unchecked
            user.setPreferences((Map<String, Object>) prefsRaw);
        }

        try {
            Object raw = snap.get("deletedAt");
            if (raw instanceof com.google.firebase.Timestamp) {
                user.setDeletedAt(((com.google.firebase.Timestamp) raw).toDate().getTime());
            } else if (raw instanceof Long) {
                user.setDeletedAt((Long) raw);
            } else if (raw instanceof Number) {
                user.setDeletedAt(((Number) raw).longValue());
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse deletedAt", e);
        }

        Object guestRaw = snap.get("isGuest");
        if (guestRaw instanceof Boolean) {
            user.setIsGuest((Boolean) guestRaw);
        } else {
            String lastLogin = user.getLastLoginAt();
            user.setIsGuest(lastLogin == null || lastLogin.isEmpty());
        }

        return user;
    }

    /**
     * Merges {@code data} into the {@code users/{uuid}} document using
     * {@code SetOptions.merge()}, preserving fields not included in the map.
     *
     * @param uuid the document ID to update
     * @param data fields to merge; only these keys are written
     * @throws Exception if the Firestore write fails
     * Ref: US 01.02.01, US 01.02.02
     */
    public void updateUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    /**
     * Soft-deletes the {@code users/{uuid}} document by setting
     * {@code isDeleted = true} and {@code deletedAt = System.currentTimeMillis()}.
     * The document is retained in Firestore for audit purposes.
     *
     * @param uuid the document ID to soft-delete
     * @throws Exception if the Firestore write fails
     * Ref: US 01.02.04
     */
    public void deleteUserSync(String uuid) throws Exception {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("isDeleted", true);
        data.put("deletedAt", System.currentTimeMillis());
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    /**
     * Hard-deletes all documents in the flat {@code registrations/} collection
     * where {@code userId} equals the supplied value, using a write batch for
     * atomicity.  Called as part of the full profile deletion flow.
     *
     * @param userId the user whose registration records should be removed
     * @throws Exception if the query or batch delete fails
     * Ref: US 01.02.04
     */
    public void deleteWaitlistEntriesForUser(String userId) throws Exception {
        QuerySnapshot snapshot = Tasks.await(
                db.collection("registrations")
                        .whereEqualTo("userId", userId)
                        .get());

        if (snapshot.isEmpty()) return;

        WriteBatch batch = db.batch();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            batch.delete(doc.getReference());
        }
        Tasks.await(batch.commit());
    }

    /** Returns the string value of {@code key}, or {@code ""} if absent or null. */
    private String getString(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v != null ? v.toString() : "";
    }

    /** Returns the boolean value of {@code key}, or {@code false} if absent or not a Boolean. */
    private boolean getBoolean(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    /** Returns the long value of {@code key}, or {@code 0} if absent or not a number. */
    private long getLong(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Long)   return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}
