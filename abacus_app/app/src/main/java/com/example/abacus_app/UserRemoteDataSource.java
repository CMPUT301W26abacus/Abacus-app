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

    public void createUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(db.collection(COLLECTION).document(uuid).set(data));
    }

    public User getUserSync(String uuid) throws Exception {
        DocumentSnapshot snap = Tasks.await(
                db.collection(COLLECTION).document(uuid).get());

        if (!snap.exists()) return null;

        User user = new User();
        // Priority: Use the actual document ID as the unique UID
        user.setUid(snap.getId()); 
        
        user.setName(getString(snap, "name"));
        user.setEmail(getString(snap, "email"));
        user.setPhone(getString(snap, "phone"));
        user.setCreatedAt(getString(snap, "createdAt"));
        user.setDeleted(getBoolean(snap, "isDeleted"));
        user.setLastLoginAt(getLong(snap, "lastLoginAt"));
        
        // Fetch role - default to entrant if missing
        String role = getString(snap, "role");
        user.setRole((role == null || role.isEmpty()) ? "entrant" : role);
        
        user.setNotificationsEnabled(getBoolean(snap, "notificationsEnabled"));

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
            user.setIsGuest(user.getLastLoginAt() == 0);
        }

        return user;
    }

    public void updateUserSync(String uuid, Map<String, Object> data) throws Exception {
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    public void deleteUserSync(String uuid) throws Exception {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("isDeleted", true);
        data.put("deletedAt", System.currentTimeMillis());
        Tasks.await(
                db.collection(COLLECTION).document(uuid)
                        .set(data, SetOptions.merge()));
    }

    private String getString(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v != null ? v.toString() : "";
    }

    private boolean getBoolean(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    private long getLong(DocumentSnapshot snap, String key) {
        Object v = snap.get(key);
        if (v instanceof Long)   return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}