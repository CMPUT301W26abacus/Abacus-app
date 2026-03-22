package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
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
 */
public class RegistrationRemoteDataSource {

    private final FirebaseFirestore firestore;

    public RegistrationRemoteDataSource() {
        firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference getCollectionRef() {
        return firestore.collection("registrations");
    }

    /**
     * Helper to extract WaitlistEntry from a document.
     *
     * Handles both authenticated docs (userId / eventId) and guest docs
     * (guestEmail / guestName / eventId). For guest docs, userId is populated
     * with the guestEmail so the rest of the history pipeline can treat them
     * identically without needing to know the difference.
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

    public boolean isUserOnWaitlistSync(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef().document(userID + "_" + eventID).get()
        );
        return doc.exists();
    }

    public WaitlistEntry getUserWaitlistEntry(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef().document(userID + "_" + eventID).get()
        );
        return mapDocument(doc);
    }

    public int getWaitlistSizeSync(String eventID) throws Exception {
        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef().whereEqualTo("eventId", eventID).get(Source.SERVER)
        );
        if (snapshot.isEmpty()) {
            snapshot = Tasks.await(
                    getCollectionRef().whereEqualTo("eventID", eventID).get(Source.SERVER));
        }
        return snapshot.size();
    }

    public void joinWaitlistSync(String eventID, WaitlistEntry entry) throws Exception {
        DocumentReference docRef = getCollectionRef()
                .document(entry.getUserId() + "_" + eventID);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("userId",        entry.getUserId());
        data.put("eventId",       entry.getEventId());
        data.put("status",        entry.getStatus());
        data.put("timestamp",     entry.getTimestamp());
        data.put("lotteryNumber", entry.getLotteryNumber());
        Tasks.await(docRef.set(data));
    }

    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.delete());
    }

    public void updateUserEntryStatusSync(String eventId, String userId,
                                          String status) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.update("status", status));
    }

    public ArrayList<WaitlistEntry> getEntriesSync(String eventID) throws Exception {
        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef().whereEqualTo("eventId", eventID).get());
        if (snapshot.isEmpty()) {
            snapshot = Tasks.await(
                    getCollectionRef().whereEqualTo("eventID", eventID).get());
        }
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            WaitlistEntry entry = mapDocument(doc);
            if (entry != null) waitlist.add(entry);
        }
        return waitlist;
    }

    public ArrayList<WaitlistEntry> getEntriesWithStatusSync(String eventID,
                                                             String status) throws Exception {
        QuerySnapshot snapshot = Tasks.await(getCollectionRef()
                .whereEqualTo("eventId", eventID)
                .whereEqualTo("status", status)
                .get());
        if (snapshot.isEmpty()) {
            snapshot = Tasks.await(getCollectionRef()
                    .whereEqualTo("eventID", eventID)
                    .whereEqualTo("status", status)
                    .get());
        }
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            WaitlistEntry entry = mapDocument(doc);
            if (entry != null) waitlist.add(entry);
        }
        return waitlist;
    }

    /**
     * Returns all registrations for an authenticated user, querying by userId.
     * Falls back to userID (capital D) for older documents.
     */
    public ArrayList<WaitlistEntry> getHistoryForUserSync(
            String userID) throws ExecutionException, InterruptedException {
        try {
            QuerySnapshot snapshot = Tasks.await(
                    getCollectionRef().whereEqualTo("userId", userID).get());
            if (snapshot.isEmpty()) {
                snapshot = Tasks.await(
                        getCollectionRef().whereEqualTo("userID", userID).get());
            }
            ArrayList<WaitlistEntry> history = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) history.add(entry);
            }
            return history;
        } catch (Exception e) {
            Log.e("RDS", "getHistoryForUserSync failed", e);
        }
        return new ArrayList<>();
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
    public ArrayList<WaitlistEntry> getHistoryForGuestSync(
            String guestEmail) throws ExecutionException, InterruptedException {
        try {
            QuerySnapshot snapshot = Tasks.await(
                    getCollectionRef().whereEqualTo("guestEmail", guestEmail).get());

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