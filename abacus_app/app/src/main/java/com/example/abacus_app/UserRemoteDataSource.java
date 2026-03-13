package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.Map;

public class UserRemoteDataSource {

    private static final String TAG        = "UserRemoteDataSource";
    private static final String COLLECTION = "users";

    private final FirebaseFirestore db;

    public UserRemoteDataSource(FirebaseFirestore db) {
        this.db = db;
    }

    /** Create a new user document. Blocks — call on background thread. */
    public void createUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(db.collection(COLLECTION).document(uuid).set(data));
    }

    /** Spec-named alias for {@link #createUserSync}. */
    public void createUser(String deviceId, Map<String, Object> data) throws Exception {
        createUserSync(deviceId, data);
    }

    /**
     * Read a user document and map it to a User object.
     * deletedAt is stored as a long (epoch ms) — never a Timestamp.
     * Blocks — call on background thread.
     */
    public User getUserSync(String uuid) throws Exception {
        DocumentSnapshot snap = Tasks.await(
                db.collection(COLLECTION).document(uuid).get());

        if (!snap.exists()) return null;

        // Map manually to avoid Timestamp→String deserialization crash
        User user = new User();
        user.setUid(uuid);
        user.setName(getString(snap, "name"));
        user.setEmail(getString(snap, "email"));
        user.setPhone(getString(snap, "phone"));
        user.setCreatedAt(getString(snap, "createdAt"));
        user.setDeleted(getBoolean(snap, "isDeleted"));
        user.setLastLoginAt(getLong(snap, "lastLoginAt"));

        // deletedAt may be stored as Timestamp (old data) or long — handle both
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
            Log.w(TAG, "Could not parse deletedAt, defaulting to 0", e);
        }

        // isGuest: Firestore stores "isGuest" key
        Object guestRaw = snap.get("isGuest");
        if (guestRaw instanceof Boolean) {
            user.setIsGuest((Boolean) guestRaw);
        } else {
            // default: treat as guest if we can't determine
            user.setIsGuest(user.getLastLoginAt() == 0);
        }

        return user;
    }

    /** Spec-named alias for {@link #getUserSync}. */
    public User getUser(String deviceId) throws Exception {
        return getUserSync(deviceId);
    }

    /**
     * Merge-update a user document.
     * Uses set+merge so it works even if the doc doesn't exist yet.
     * Blocks — call on background thread.
     */
    public void updateUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    /** Spec-named alias for {@link #updateUserSync}. */
    public void updateUser(String deviceId, Map<String, Object> data) throws Exception {
        updateUserSync(deviceId, data);
    }

    /** Soft-delete: set isDeleted=true, deletedAt=now (as epoch ms long). */
    public void deleteUserSync(String uuid) throws Exception {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("isDeleted", true);
        data.put("deletedAt", System.currentTimeMillis());
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    /** Spec-named alias for {@link #deleteUserSync}. */
    public void deleteUser(String deviceId) throws Exception {
        deleteUserSync(deviceId);
    }

    /**
     * Safely retrieves a String value from a Firestore DocumentSnapshot.
     * Returns an empty string if the value is null or not a String.
     * @param snap Firestore DocumentSnapshot
     * @param key  Key to retrieve
     * @return     String value or empty string
     */
    private String getString(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v != null ? v.toString() : "";
    }

    /**
     * Safely retrieves a Boolean value from a Firestore DocumentSnapshot.
     * Returns false if the value is null or not a Boolean.
     * @param snap Firestore DocumentSnapshot
     * @param key  Key to retrieve
     * @return     Boolean value or false
     */
    private boolean getBoolean(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    /**
     * Safely retrieves a Long value from a Firestore DocumentSnapshot.
     * Returns 0L if the value is null or not a Long or Number.
     * @param snap Firestore DocumentSnapshot
     * @param key  Key to retrieve
     * @return     Long value or 0L
     */
    private long getLong(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Long)   return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}