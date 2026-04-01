package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
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

        return new WaitlistEntry(userId, eventId, status, lotteryNumber, timestamp);
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
            return doc.toObject(WaitlistEntry.class);
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
        Tasks.await(docRef.set(entry));
    }

    /**
     * Removes a user's entry from the waitlist subcollection.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId  the unique ID of the user in the database
     * @throws Exception something went wrong
     */
    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {
        DocumentReference docRef = getCollectionRef(eventId).document(userId);
        Tasks.await(docRef.delete());
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
            QuerySnapshot snapshot = Tasks.await(
                    firestore.collectionGroup("waitlist")
                            .whereEqualTo("userID", userID)
                            .get()
            );

            ArrayList<WaitlistEntry> history = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                history.add(entry);
            }
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
}