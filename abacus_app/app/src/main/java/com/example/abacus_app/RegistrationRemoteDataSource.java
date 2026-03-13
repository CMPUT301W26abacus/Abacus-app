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
 * Performs CRUD operations on the waitlist data in the remote Firestore database.
 * Interacts with the 'registrations' collection.
 * 
 * @author Himesh
 * @version 1.0
 */
public class RegistrationRemoteDataSource {

    private final FirebaseFirestore firestore;

    /**
     * Initializes the Firestore instance.
     */
    public RegistrationRemoteDataSource() {
        firestore = FirebaseFirestore.getInstance();
    }

    /**
     * @return A reference to the 'registrations' collection in Firestore.
     */
    private CollectionReference getCollectionRef() {
        return firestore.collection("registrations");
    }

    /**
     * Manually extracts a {@link WaitlistEntry} from a Firestore {@link DocumentSnapshot}.
     * Ensures compatibility between field naming conventions (e.g., userId vs userID).
     * 
     * @param doc The Firestore document snapshot.
     * @return A populated {@link WaitlistEntry}, or null if the document does not exist.
     */
    private WaitlistEntry mapDocument(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        
        String userId = doc.getString("userId");
        if (userId == null) userId = doc.getString("userID");
        
        String eventId = doc.getString("eventId");
        if (eventId == null) eventId = doc.getString("eventID");
        
        String status = doc.getString("status");
        Long timestamp = doc.getLong("timestamp");
        Long lottoLong = doc.getLong("lotteryNumber");
        Integer lotteryNumber = lottoLong != null ? lottoLong.intValue() : 0;

        return new WaitlistEntry(userId, eventId, status, lotteryNumber, timestamp);
    }

    /**
     * Synchronously checks if a user is already on the waitlist for a specific event.
     */
    public boolean isUserOnWaitlistSync(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef().document(userID + "_" + eventID).get()
        );
        return doc.exists();
    }

    /**
     * Synchronously retrieves a user's waitlist entry for a specific event.
     */
    public WaitlistEntry getUserWaitlistEntry(String userID, String eventID) throws Exception {
        DocumentSnapshot doc = Tasks.await(
                getCollectionRef().document(userID + "_" + eventID).get()
        );
        return mapDocument(doc);
    }

    /**
     * Synchronously counts the number of entrants on the waitlist for an event.
     */
    public int getWaitlistSizeSync(String eventID) throws Exception {
        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef().whereEqualTo("eventId", eventID).get(Source.SERVER)
        );
        if (snapshot.isEmpty()) {
            snapshot = Tasks.await(getCollectionRef().whereEqualTo("eventID", eventID).get(Source.SERVER));
        }
        return snapshot.size();
    }

    /**
     * Synchronously saves a new waitlist entry to Firestore.
     */
    public void joinWaitlistSync(String eventID, WaitlistEntry entry) throws Exception {
        DocumentReference docRef = getCollectionRef().document(entry.getUserId() + "_" + eventID);
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("userId", entry.getUserId());
        data.put("eventId", entry.getEventId());
        data.put("status", entry.getStatus());
        data.put("timestamp", entry.getTimestamp());
        data.put("lotteryNumber", entry.getLotteryNumber());
        Tasks.await(docRef.set(data));
    }

    /**
     * Synchronously deletes a waitlist entry from Firestore.
     */
    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.delete());
    }

    /**
     * Synchronously updates the status of a waitlist entry.
     */
    public void updateUserEntryStatusSync(String eventId, String userId, String status) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.update("status", status));
    }

    /**
     * Synchronously retrieves all entries for a specific event.
     */
    public ArrayList<WaitlistEntry> getEntriesSync(String eventID) throws Exception {
        QuerySnapshot snapshot = Tasks.await(getCollectionRef().whereEqualTo("eventId", eventID).get());
        if (snapshot.isEmpty()) {
            snapshot = Tasks.await(getCollectionRef().whereEqualTo("eventID", eventID).get());
        }

        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            WaitlistEntry entry = mapDocument(doc);
            if (entry != null) waitlist.add(entry);
        }
        return waitlist;
    }

    /**
     * Synchronously retrieves entries for an event filtered by status (e.g., 'waitlisted').
     */
    public ArrayList<WaitlistEntry> getEntriesWithStatusSync(String eventID, String status) throws Exception {
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
     * Synchronously retrieves the registration history for a user across all events.
     */
    public ArrayList<WaitlistEntry> getHistoryForUserSync(String userID) throws ExecutionException, InterruptedException {
        try {
            QuerySnapshot snapshot = Tasks.await(getCollectionRef().whereEqualTo("userId", userID).get());
            if (snapshot.isEmpty()) {
                snapshot = Tasks.await(getCollectionRef().whereEqualTo("userID", userID).get());
            }

            ArrayList<WaitlistEntry> history = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                WaitlistEntry entry = mapDocument(doc);
                if (entry != null) history.add(entry);
            }
            return history;
        } catch (Exception e) {
            Log.e("RDS", "query failed", e);
        }
        return null;
    }
}
