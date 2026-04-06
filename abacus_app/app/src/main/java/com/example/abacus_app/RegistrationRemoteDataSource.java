package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Performs CRUD operations on the waitlist data in remote Firestore database.
 * Maps data from the 'registrations' collection.
 *
 * Guest registrations use a different document structure — no userId field,
 * instead they have guestEmail and guestName. getHistoryForGuestSync() handles
 * this case by querying on guestEmail rather than userId.
 *
 * NOTE: The methods in this class run SYNCHRONOUSLY and are only to be used in the architecture
 * layer (repositories). For methods related to UI, refer to {@link RegistrationRepository}.
 *
 * @author Kaylee
 */
public class RegistrationRemoteDataSource {

    private final FirebaseFirestore firestore;

    /**
     * Constructs a RegistrationRemoteDataSource object which can be used to read/write to the
     * waitlist of an event in the Firestore database.
     *
     * NOTE: This class should only be utilized from repository classes.
     */
    public RegistrationRemoteDataSource() {
        firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference getCollectionRef(String eventID) {
        return firestore
                .collection("events")
                .document(eventID)
                .collection("waitlist");
    }

    private DocumentReference getEventDocRef(String eventID) {
        return firestore.collection("events").document(eventID);
    }

    /**
     * Helper to extract WaitlistEntry from a document.
     *
     * Handles both authenticated docs (userId / eventId) and guest docs
     * (guestEmail / guestName / eventId). For guest docs, userId is populated
     * with the guestEmail so the rest of the history pipeline can treat them
     * identically without needing to know the difference.
     *
     * WaitlistEntry does not use firebase serialization.
     */
    private WaitlistEntry mapDocument(DocumentSnapshot doc) {
        if (!doc.exists()) return null;

        String userId = doc.getString("userId");
        if (userId == null) userId = doc.getString("userID");

        // Guest docs have no userId — fall back to guestEmail so the entry
        // still carries a non-null identifier through the history pipeline.
        if (userId == null) {
            String guestEmail = doc.getString("guestEmail");
            if (guestEmail != null) userId = guestEmail;
        }

        String eventId = doc.getString("eventId");
        if (eventId == null) eventId = doc.getString("eventID");

        String status = doc.getString("status");

        Long timestamp = doc.getLong("timestamp");

        Long lottoLong = doc.getLong("lotteryNumber");
        Integer lotteryNumber = lottoLong != null ? lottoLong.intValue() : 0;

        WaitlistEntry entry = new WaitlistEntry(userId, eventId, status, lotteryNumber, timestamp);
        entry.setLatitude(doc.getDouble("latitude"));
        entry.setLongitude(doc.getDouble("longitude"));
        entry.setUserName(doc.getString("userName"));
        entry.setUserEmail(doc.getString("userEmail"));
        
        return entry;
    }

    // ── Existing methods (unchanged) ──────────────────────────────────────────

    /**
     * Checks whether or not a specific user is on the waitlist of a specific event.
     *
     * @param userID  the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @return true if user is on waitlist, false otherwise
     * @throws Exception something went wrong
     */
    public boolean isUserOnWaitlistSync(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef(eventID)
                        .document(userID)
                        .get()
        );
        return doc.exists();
    }

    /**
     * Returns a WaitlistEntry object for a single user on a single event.
     *
     * @param userID  the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @return the WaitlistEntry or null if not found
     * @throws Exception something went wrong
     */
    public WaitlistEntry getUserWaitlistEntry(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef(eventID)
                        .document(userID)
                        .get()
        );
        if (doc.exists()) {
            return mapDocument(doc);
        }
        return null;
    }

    /**
     * Returns the number of entries on the waitlist for an event.
     *
     * @param eventID the unique ID of the event in the database
     * @return size of the waitlist
     * @throws Exception something went wrong
     */
    public int getWaitlistSizeSync(String eventID) throws Exception {
        Log.d("mytagRDS", "Running from RDS...");
        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef(eventID).get(Source.SERVER)
        );
        return snapshot.size();
    }

    /**
     * Adds a WaitlistEntry to the waitlist subcollection of an event.
     *
     * @param eventID the unique ID of the event in the database
     * @param entry   the WaitlistEntry to add
     * @throws Exception something went wrong
     */
    public void joinWaitlistSync(String eventID, WaitlistEntry entry) throws Exception {
        DocumentReference docRef = getCollectionRef(eventID).document(entry.getUserID());
        
        // Use a transaction or batch to ensure count consistency if needed, 
        // but simple update is usually enough for this scope.
        Tasks.await(docRef.set(entry));
        
        // Increment waitlistCount in the event document
        Tasks.await(getEventDocRef(eventID).update("waitlistCount", FieldValue.increment(1)));
    }

    /**
     * Atomically joins the waitlist using a Firestore transaction.
     *
     * <p>Performs three checks inside the transaction (read-then-write, no TOCTOU race):
     * <ol>
     *   <li>Duplicate check — throws {@link IllegalArgumentException} if the user is
     *       already on the waitlist.</li>
     *   <li>Capacity check — throws {@link IllegalStateException} if
     *       {@code waitlistCapacity} is set and the waitlist is already full.</li>
     *   <li>Atomic write — adds the waitlist entry document and increments
     *       {@code Event.waitlistCount} in the same transaction commit.</li>
     * </ol>
     *
     * <p>Use this method instead of {@link #joinWaitlistSync} whenever the event
     * has a capacity limit, to prevent over-subscription under concurrent load.
     *
     * @param eventID the unique ID of the event in the database
     * @param entry   the {@link WaitlistEntry} to add (must have a non-null userId)
     * @throws IllegalArgumentException if the user is already on the waitlist
     * @throws IllegalStateException    if the waitlist is at capacity
     * @throws Exception                if the Firestore transaction fails for any other reason
     */
    public void joinWaitlistAtomicSync(String eventID, WaitlistEntry entry) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference eventRef   = db.collection("events").document(eventID);
        DocumentReference entrantRef = getCollectionRef(eventID).document(entry.getUserID());

        Tasks.await(db.runTransaction(transaction -> {
            DocumentSnapshot eventSnap   = transaction.get(eventRef);
            DocumentSnapshot entrantSnap = transaction.get(entrantRef);

            // 1. Duplicate check
            if (entrantSnap.exists()) {
                throw new IllegalArgumentException("User is already on the waitlist.");
            }

            // 2. Capacity check (null waitlistCapacity = unlimited)
            Long capacity = eventSnap.getLong("waitlistCapacity");
            Long count    = eventSnap.getLong("waitlistCount");
            long current  = count != null ? count : 0L;
            if (capacity != null && capacity > 0 && current >= capacity) {
                throw new IllegalStateException("Waitlist is full.");
            }

            // 3. Atomic write
            transaction.set(entrantRef, entry);
            transaction.update(eventRef, "waitlistCount", FieldValue.increment(1));
            return null;
        }));
    }

    /**
     * Removes a user's entry from the waitlist subcollection.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId  the unique ID of the user in the database
     * @throws Exception something went wrong
     */
    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference eventRef = getEventDocRef(eventId);
        DocumentReference entrantRef = getCollectionRef(eventId).document(userId);

        Tasks.await(db.runTransaction(transaction -> {
            DocumentSnapshot entrantSnap = transaction.get(entrantRef);

            if (!entrantSnap.exists()) {
                return null;
            }

            transaction.delete(entrantRef);
            transaction.update(eventRef, "waitlistCount", FieldValue.increment(-1));
            return null;
        }));
    }

    /**
     * Updates the status field of a single waitlist entry.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId  the unique ID of the user in the database
     * @param status  the new status string
     * @throws Exception something went wrong
     */
    public void updateUserEntryStatusSync(String eventId, String userId, String status) throws Exception {
        DocumentReference docRef = getCollectionRef(eventId).document(userId);
        Tasks.await(docRef.update("status", status));
    }

    /**
     * Returns all waitlist entries for a specific event.
     *
     * @param eventID the unique ID of the event in the database
     * @return list of all WaitlistEntry objects
     * @throws Exception something went wrong
     */
    public ArrayList<WaitlistEntry> getEntriesSync(String eventID) throws Exception {
        QuerySnapshot snapshot = Tasks.await(getCollectionRef(eventID).get());

        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            WaitlistEntry entry = mapDocument(doc);
            waitlist.add(entry);
        }
        return waitlist;
    }

    /**
     * Returns all waitlist entries for a specific event filtered by status.
     *
     * @param eventID the unique ID of the event in the database
     * @param status  the status to filter by
     * @return filtered list of WaitlistEntry objects
     * @throws Exception something went wrong
     */
    public ArrayList<WaitlistEntry> getEntriesWithStatusSync(String eventID, String status) throws Exception {
        QuerySnapshot snapshot = Tasks.await(getCollectionRef(eventID).get());

        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            if (status.equals(doc.getString("status"))) {
                WaitlistEntry entry = mapDocument(doc);
                waitlist.add(entry);
            }
        }
        return waitlist;
    }

    /**
     * Returns all waitlist entries associated with a single user across all events.
     *
     * @param userID the unique ID of the user in the database
     * @return list of all WaitlistEntry objects for the user
     */
    public ArrayList<WaitlistEntry> getHistoryForUserSync(String userID) throws ExecutionException, InterruptedException {
        try {
            Map<String, WaitlistEntry> merged = new LinkedHashMap<>();

            // Fast indexed collection group query: find all waitlist entries for this user across all events
            QuerySnapshot primarySnapshot = Tasks.await(
                    firestore.collectionGroup("waitlist")
                            .whereEqualTo("userId", userID)
                            .get()
            );
            for (DocumentSnapshot doc : primarySnapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) merged.put(doc.getReference().getPath(), entry);
            }

            // Legacy fallback: older docs may have used userID (uppercase) instead of userId
            QuerySnapshot legacySnapshot = Tasks.await(
                    firestore.collectionGroup("waitlist")
                            .whereEqualTo("userID", userID)
                            .get()
            );
            for (DocumentSnapshot doc : legacySnapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) merged.put(doc.getReference().getPath(), entry);
            }

            // Backward compatibility for older flows that wrote only to flat registrations collection
            QuerySnapshot registrationsSnapshot = Tasks.await(
                    firestore.collection("registrations")
                            .whereEqualTo("userId", userID)
                            .get()
            );
            for (DocumentSnapshot doc : registrationsSnapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) merged.put(doc.getReference().getPath(), entry);
            }

            ArrayList<WaitlistEntry> history = new ArrayList<>();
            history.addAll(merged.values());
            return history;

        } catch (Exception e) {
            Log.e("RDS", "query failed", e);
        }
        return null;
    }

    /**
     * Returns all registrations for a guest user, querying by guestEmail.
     *
     * Guest docs have no userId field — they are keyed by sanitised email
     * as the document ID prefix and store the raw email in the guestEmail field.
     * This method is used by FirebaseRegistrationRepository when the app is
     * running in guest mode (isGuest intent extra == true).
     *
     * @param guestEmail the raw email address entered by the guest
     * @return list of WaitlistEntry items, or empty list on failure
     */
    // users should not be allowed to join waitlists without being identified (signed in)
    public ArrayList<WaitlistEntry> getHistoryForGuestSync(
            String guestEmail) throws ExecutionException, InterruptedException {
        try {
            QuerySnapshot snapshot = Tasks.await(
                    firestore.collection("registrations").whereEqualTo("guestEmail", guestEmail).get());

            ArrayList<WaitlistEntry> history = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) history.add(entry);
            }
            return history;
        } catch (Exception e) {
            Log.e("RDS", "getHistoryForGuestSync failed", e);
        }
        return new ArrayList<>();
    }

    public void addGuestFields(String eventId, String guestId, String guestEmail, String guestName) throws Exception {
        DocumentReference docRef = getCollectionRef(eventId).document(guestId);

        // Data to add
        Map<String, Object> updates = new HashMap<>();
        updates.put("guestEmail", guestEmail);
        updates.put("guestName", guestName);
        updates.put("isGuest", true);

        // Update only if document exists
        DocumentSnapshot snapshot = Tasks.await(docRef.get());
        if (snapshot.exists()) {
            Tasks.await(docRef.update(updates));
        }
    }
}