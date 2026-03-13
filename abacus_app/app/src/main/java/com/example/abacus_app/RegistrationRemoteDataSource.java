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
     * Helper to extract WaitlistEntry from a document manually to ensure field matching.
     */
    private WaitlistEntry mapDocument(DocumentSnapshot doc) {
        if (!doc.exists()) return null;
        
        // Try both camelCase and all lowercase if needed, 
        // but user specifically said 'userId' and 'eventId'
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
            // Try matching case variations if zero results
            snapshot = Tasks.await(getCollectionRef().whereEqualTo("eventID", eventID).get(Source.SERVER));
        }
        return snapshot.size();
    }

    public void joinWaitlistSync(String eventID, WaitlistEntry entry) throws Exception {
        DocumentReference docRef = getCollectionRef().document(entry.getUserId() + "_" + eventID);
        // We set the fields explicitly to match user's structure
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("userId", entry.getUserId());
        data.put("eventId", entry.getEventId());
        data.put("status", entry.getStatus());
        data.put("timestamp", entry.getTimestamp());
        data.put("lotteryNumber", entry.getLotteryNumber());
        Tasks.await(docRef.set(data));
    }

    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.delete());
    }

    public void updateUserEntryStatusSync(String eventId, String userId, String status) throws Exception {
        DocumentReference docRef = getCollectionRef().document(userId + "_" + eventId);
        Tasks.await(docRef.update("status", status));
    }

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
