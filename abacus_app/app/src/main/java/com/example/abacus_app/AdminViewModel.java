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
 * ViewModel for the admin moderation screens.
 *
 * <p>Owns and exposes two primary data streams — a list of {@link Event} objects whose
 * poster images are subject to moderation, and a list of {@link User} profiles that can
 * be soft-deleted. Both streams are backed by Firestore and re-loaded on demand.
 *
 * <p><b>Design pattern:</b> Standard Android MVVM. All Firestore interactions are
 * initiated here; the Fragment layer observes {@link LiveData} and never touches the
 * database directly. A shared {@code searchQuery} LiveData is used so both tabs
 * ({@code Images} and {@code Profiles}) filter reactively from a single search bar.
 *
 * <p><b>Lifecycle:</b> Scoped to the Activity via {@code ViewModelProvider}, so the
 * data survives configuration changes (screen rotations) without re-fetching.
 *
 * <p><b>Known issues / outstanding work:</b>
 * <ul>
 *   <li>Both {@link #loadImages()} and {@link #loadProfiles()} fetch the entire collection
 *       without pagination. This will become slow once the dataset exceeds a few hundred
 *       documents. Add Firestore query cursors ({@code startAfter}) and a paging trigger.</li>
 *   <li>{@link #deleteImage(String)} nulls out {@code posterImageUrl} but does not remove
 *       the image from Firebase Storage, leaving orphaned blobs. A Cloud Function or a
 *       Storage cleanup job is needed.</li>
 *   <li>The {@code error} LiveData is never cleared after being observed, so the same
 *       error Toast can fire again after a configuration change. Wrap it in a
 *       {@code SingleLiveEvent} or clear it after consumption.</li>
 *   <li>The {@link #mapUser(DocumentSnapshot)} fallback for {@code isGuest} (checking
 *       {@code lastLoginAt}) may misidentify real users who have not logged in recently.</li>
 * </ul>
 */
public class AdminViewModel extends ViewModel {

    private static final String TAG = "AdminViewModel";

    /** Firestore client — injected via singleton for now; consider constructor injection for testing. */
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ── LiveData fields ───────────────────────────────────────────────────────

    /** Events that have a non-empty {@code posterImageUrl} and are not soft-deleted. */
    private final MutableLiveData<List<Event>> images   = new MutableLiveData<>(new ArrayList<>());

    /** All user profiles (including guests); excluded from the UI if {@code isDeleted == true}. */
    private final MutableLiveData<List<User>>  profiles = new MutableLiveData<>(new ArrayList<>());

    /** One-shot error message string; cleared by wrapping in {@code SingleLiveEvent} (TODO). */
    private final MutableLiveData<String>      error    = new MutableLiveData<>();

    /** Current search string typed into the shared search bar; lowercase, trimmed. */
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");

    // ── Public LiveData accessors ─────────────────────────────────────────────

    /**
     * Returns the stream of moderable {@link Event} objects (those with a poster image).
     *
     * @return non-null {@link LiveData}; emits an empty list before the first load completes
     */
    public LiveData<List<Event>> getImages()   { return images; }

    /**
     * Returns the stream of all {@link User} profiles loaded from Firestore.
     * Soft-deleted users are included in this list; the UI layer filters them out.
     *
     * @return non-null {@link LiveData}; emits an empty list before the first load completes
     */
    public LiveData<List<User>>  getProfiles() { return profiles; }

    /**
     * Returns the latest error message produced by any Firestore operation.
     *
     * <p><b>Note:</b> This LiveData is not cleared after delivery. Observers should
     * treat re-delivery after rotation as a known limitation until a SingleLiveEvent is used.
     *
     * @return non-null {@link LiveData}; null value means no pending error
     */
    public LiveData<String>      getError()    { return error; }

    /**
     * Returns the current search query entered by the admin.
     * The string is always lowercase and trimmed before being posted.
     *
     * @return non-null {@link LiveData}; defaults to {@code ""} (empty = no filter)
     */
    public LiveData<String> getSearchQuery() { return searchQuery; }

    // ── Search query ──────────────────────────────────────────────────────────

    /**
     * Updates the search query. Both the images tab and profiles tab observe this
     * value and re-filter their lists immediately on the main thread.
     *
     * @param query the raw text from the search bar; should already be trimmed and lowercased
     */
    public void setSearchQuery(String query) { searchQuery.setValue(query); }

    // ── Images ────────────────────────────────────────────────────────────────

    /**
     * Fetches all events from Firestore and filters to those with a non-empty
     * {@code posterImageUrl} that are not soft-deleted ({@code isDeleted != true}).
     *
     * <p>On success, posts the filtered list to {@link #images}.
     * On failure, posts a human-readable message to {@link #error}.
     *
     * <p>Documents that fail deserialization are skipped with a warning log rather than
     * crashing the entire load, matching the defensive pattern used in {@link #loadProfiles()}.
     */
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

    /**
     * Fetches all user documents from Firestore using safe manual deserialization via
     * {@link #mapUser(DocumentSnapshot)}.
     *
     * <p>Firestore's automatic {@code toObject(User.class)} was unreliable due to mixed
     * types in legacy documents (e.g. {@code deletedAt} stored as String, Long, or Timestamp),
     * so each field is extracted individually with null-safe helpers.
     *
     * <p>On success, posts the full list (including soft-deleted users) to {@link #profiles}.
     * Soft-deleted users are filtered out in the UI layer, not here, so that they can be
     * shown in a future "deleted accounts" audit view without a new Firestore fetch.
     */
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
     * Manually maps a Firestore {@link DocumentSnapshot} to a {@link User}.
     *
     * <p>This method exists because some fields in older user documents are stored with
     * inconsistent types (see the {@code deletedAt} handling below). Using
     * {@code doc.toObject(User.class)} caused crashes for those documents.
     *
     * <p>Field-by-field extraction strategy:
     * <ul>
     *   <li>{@code deletedAt} — may be a {@link Timestamp}, {@code Long}, any {@link Number},
     *       a String, or absent. Converted to epoch-milliseconds ({@code long}); defaults to
     *       {@code 0} for unrecognised types.</li>
     *   <li>{@code isGuest} — if the field is absent or not a boolean, inferred from whether
     *       {@code lastLoginAt} is populated (empty = likely a guest).</li>
     * </ul>
     *
     * @param doc a Firestore document snapshot; must {@link DocumentSnapshot#exists()}
     * @return a populated {@link User}, or {@code null} if the document does not exist
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

    /**
     * Removes the poster image from an event by setting {@code posterImageUrl} to
     * {@code null} in Firestore. The event document itself is not deleted.
     *
     * <p>On success, triggers a full {@link #loadImages()} refresh so the list stays
     * consistent with the database.
     *
     * <p><b>Known issue:</b> the underlying file in Firebase Storage is not deleted.
     * Orphaned blobs will accumulate until a cleanup job or Cloud Function is added.
     *
     * @param eventId the Firestore document ID of the event to update; must not be null
     */
    public void deleteImage(String eventId) {
        db.collection("events").document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(unused -> loadImages())
                .addOnFailureListener(e -> error.setValue("Failed to delete image: " + e.getMessage()));
    }

    /**
     * Soft-deletes a user profile by setting {@code isDeleted = true} in Firestore.
     * The document is not removed from the database; the account is simply deactivated.
     *
     * <p>On success, triggers a full {@link #loadProfiles()} refresh.
     *
     * @param userId the Firestore document ID (UID) of the user to deactivate; must not be null
     */
    public void deleteProfile(String userId) {
        db.collection("users").document(userId)
                .update("isDeleted", true)
                .addOnSuccessListener(unused -> loadProfiles())
                .addOnFailureListener(e -> error.setValue("Failed to delete profile: " + e.getMessage()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Safely reads a Firestore field as a {@link String}.
     *
     * @param doc the document to read from
     * @param key the field name
     * @return the field value as a string, or {@code ""} if the field is absent or null
     */
    private String safeString(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v != null ? v.toString() : "";
    }

    /**
     * Safely reads a Firestore field as a {@code boolean}.
     *
     * @param doc the document to read from
     * @param key the field name
     * @return {@code true} only if the stored value is literally a {@link Boolean} {@code true};
     *         returns {@code false} for absent fields, null, strings, or any other type
     */
    private boolean safeBoolean(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    /**
     * Safely reads a Firestore field as a {@code long}.
     *
     * @param doc the document to read from
     * @param key the field name
     * @return the numeric value as a {@code long}, or {@code 0} if absent or non-numeric
     */
    private long safeLong(DocumentSnapshot doc, String key) {
        Object v = doc.get(key);
        if (v instanceof Long)   return (Long) v;
        if (v instanceof Number) return ((Number) v).longValue();
        return 0L;
    }
}