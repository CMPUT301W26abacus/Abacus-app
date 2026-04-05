package com.example.abacus_app;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ViewModel for admin moderation screens (images + profiles).
 */
public class AdminViewModel extends ViewModel {

    private static final String TAG = "AdminViewModel";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<Event>> images   = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<User>>  profiles = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>      error    = new MutableLiveData<>();
    private final MutableLiveData<String>      searchQuery = new MutableLiveData<>("");

    private final Map<String, String> userEmailCache = new HashMap<>();

    public LiveData<List<Event>> getImages()   { return images; }
    public LiveData<List<User>>  getProfiles() { return profiles; }
    public LiveData<String>      getError()    { return error; }
    public LiveData<String>      getSearchQuery() { return searchQuery; }

    public void setSearchQuery(String query) { searchQuery.setValue(query); }

    // ── Images ────────────────────────────────────────────────────────────────

    /** Loads all events that have a non-null posterImageUrl and attempts to resolve organizer emails. */
    public void loadImages() {
        db.collection("events")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            Event e = doc.toObject(Event.class);
                            if (e != null
                                    && !Boolean.TRUE.equals(e.getIsDeleted())
                                    && e.getPosterImageUrl() != null
                                    && !e.getPosterImageUrl().isEmpty()) {
                                
                                // Resolve email if missing
                                if (e.getOrganizerEmail() == null || e.getOrganizerEmail().isEmpty()) {
                                    String cachedEmail = userEmailCache.get(e.getOrganizerId());
                                    if (cachedEmail != null) {
                                        e.setOrganizerEmail(cachedEmail);
                                    } else {
                                        fetchAndPopulateEmail(e);
                                    }
                                }
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

    private void fetchAndPopulateEmail(Event event) {
        if (event.getOrganizerId() == null) return;
        db.collection("users").document(event.getOrganizerId())
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String email = doc.getString("email");
                        if (email != null) {
                            userEmailCache.put(event.getOrganizerId(), email);
                            event.setOrganizerEmail(email);
                            // Refresh the list to show the new email
                            List<Event> current = images.getValue();
                            if (current != null) images.setValue(new ArrayList<>(current));
                        }
                    }
                });
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    /** Loads all user profiles and updates the email cache. */
    public void loadProfiles() {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<User> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot) {
                        try {
                            User u = mapUser(doc);
                            if (u != null) {
                                list.add(u);
                                if (u.getEmail() != null && !u.getEmail().isEmpty()) {
                                    userEmailCache.put(u.getUid(), u.getEmail());
                                }
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Skipping bad user doc: " + doc.getId(), e);
                        }
                    }
                    profiles.setValue(list);
                    // After profiles are loaded, we might have new emails for the images list
                    refreshImageEmails();
                })
                .addOnFailureListener(e -> error.setValue("Failed to load profiles: " + e.getMessage()));
    }

    private void refreshImageEmails() {
        List<Event> currentImages = images.getValue();
        if (currentImages == null) return;

        boolean changed = false;
        for (Event e : currentImages) {
            if (e.getOrganizerEmail() == null || e.getOrganizerEmail().isEmpty()) {
                String email = userEmailCache.get(e.getOrganizerId());
                if (email != null) {
                    e.setOrganizerEmail(email);
                    changed = true;
                }
            }
        }
        if (changed) images.setValue(new ArrayList<>(currentImages));
    }

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

        try {
            Object raw = doc.get("deletedAt");
            if (raw instanceof Timestamp) {
                u.setDeletedAt(((Timestamp) raw).toDate().getTime());
            } else if (raw instanceof Long) {
                u.setDeletedAt((Long) raw);
            } else if (raw instanceof Number) {
                u.setDeletedAt(((Number) raw).longValue());
            } else {
                u.setDeletedAt(0L);
            }
        } catch (Exception e) {
            u.setDeletedAt(0L);
        }

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

    public void deleteImage(String eventId) {
        db.collection("events").document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(unused -> loadImages())
                .addOnFailureListener(e -> error.setValue("Failed to delete image: " + e.getMessage()));
    }

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
}
