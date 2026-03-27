package com.example.abacus_app;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for admin moderation screens (images + profiles).
 */
public class AdminViewModel extends ViewModel {

    private static final String TAG = "AdminViewModel";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<Event>> images   = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<User>>  profiles = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>      error    = new MutableLiveData<>();

    public LiveData<List<Event>> getImages()   { return images; }
    public LiveData<List<User>>  getProfiles() { return profiles; }
    public LiveData<String>      getError()    { return error; }

    // ── Images ────────────────────────────────────────────────────────────────

    /** Loads all events that have a non-null posterImageUrl. */
    public void loadImages() {
        db.collection("events")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            Event e = doc.toObject(Event.class);
                            if (e != null
                                    && e.getPosterImageUrl() != null
                                    && !e.getPosterImageUrl().isEmpty()) {
                                list.add(e);
                            }
                        } catch (Exception ex) {
                            Log.w(TAG, "Skipping bad event doc: " + doc.getId(), ex);
                        }
                    }
                    images.setValue(list);
                })
                .addOnFailureListener(e -> error.setValue("Failed to load images: " + e.getMessage()));
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    /** Loads all user profiles using safe manual deserialization. */
    public void loadProfiles() {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            User u = mapUser(doc);
                            if (u != null) list.add(u);
                        } catch (Exception e) {
                            Log.w(TAG, "Skipping bad user doc: " + doc.getId(), e);
                        }
                    }
                    profiles.setValue(list);
                })
                .addOnFailureListener(e -> error.setValue("Failed to load profiles: " + e.getMessage()));
    }

    /**
     * Manually maps a Firestore DocumentSnapshot to a User object.
     * Handles missing fields and mixed types (e.g. deletedAt stored as
     * String, Long, or Timestamp depending on how old the document is).
     */
    private User mapUser(DocumentSnapshot doc) {
        if (!doc.exists()) return null;

        User u = new User();
        u.setUid(doc.getId());
        u.setName(safeString(doc, "name"));
        u.setEmail(safeString(doc, "email"));
        u.setPhone(safeString(doc, "phone"));
        u.setCreatedAt(safeString(doc, "createdAt"));
        u.setDeleted(safeBoolean(doc, "isDeleted"));
        u.setLastLoginAt(safeString(doc, "lastLoginAt"));
        u.setRole(safeString(doc, "role"));
        u.setNotificationsEnabled(safeBoolean(doc, "notificationsEnabled"));

        // deletedAt: may be missing, String, Long, Number, or Timestamp
        try {
            Object raw = doc.get("deletedAt");
            if (raw instanceof Timestamp) {
                u.setDeletedAt(((Timestamp) raw).toDate().getTime());
            } else if (raw instanceof Long) {
                u.setDeletedAt((Long) raw);
            } else if (raw instanceof Number) {
                u.setDeletedAt(((Number) raw).longValue());
            } else {
                u.setDeletedAt(0L); // null, String, or unknown — default safely
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not parse deletedAt for " + doc.getId() + ", defaulting to 0", e);
            u.setDeletedAt(0L);
        }

        // isGuest: fall back to checking lastLoginAt if field is missing
        Object guestRaw = doc.get("isGuest");
        if (guestRaw instanceof Boolean) {
            u.setIsGuest((Boolean) guestRaw);
        } else {
            String lastLogin = u.getLastLoginAt();
            u.setIsGuest(lastLogin == null || lastLogin.isEmpty());
        }

        return u;
    }

    // ── Mutations ─────────────────────────────────────────────────────────────

    /** Deletes a poster image URL from an event (nulls out the field). */
    public void deleteImage(String eventId) {
        db.collection("events").document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(unused -> loadImages())
                .addOnFailureListener(e -> error.setValue("Failed to delete image: " + e.getMessage()));
    }

    /** Soft-deletes a user profile (sets isDeleted = true). */
    public void deleteProfile(String userId) {
        db.collection("users").document(userId)
                .update("isDeleted", true)
                .addOnSuccessListener(unused -> loadProfiles())
                .addOnFailureListener(e -> error.setValue("Failed to delete profile: " + e.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String safeString(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v != null ? v.toString() : "";
    }

    private boolean safeBoolean(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    private long safeLong(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Long)   return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}