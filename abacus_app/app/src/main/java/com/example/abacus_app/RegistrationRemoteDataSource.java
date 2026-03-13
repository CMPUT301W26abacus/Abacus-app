package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Task;
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
 *
 * NOTE: The methods in this class run SYNCHRONOUSLY and are only to be used in the architecture
 * layer (repositories). For methods related to UI, refer to {@link RegistrationRepository}.
 *
 * @author Team Abacus, Kaylee Crocker
 * @version 1.0
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
            String userID = doc.getString("userID");
            String status = doc.getString("status");
            Long lotteryNumberLong = doc.getLong("lotteryNumber");
            int lotteryNumber = lotteryNumberLong != null ? lotteryNumberLong.intValue() : 0;
            Timestamp joinTime = doc.getTimestamp("joinTime");
            waitlist.add(new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime));
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
                String userID = doc.getString("userID");
                Long lotteryNumberLong = doc.getLong("lotteryNumber");
                int lotteryNumber = lotteryNumberLong != null ? lotteryNumberLong.intValue() : 0;
                Timestamp joinTime = doc.getTimestamp("joinTime");
                waitlist.add(new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime));
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
                // Extract eventID from document path: events/{eventID}/waitlist/{userID}
                String eventID = doc.getReference().getParent().getParent().getId();
                
                String status = doc.getString("status");
                Long lotteryNumLong = doc.getLong("lotteryNumber");
                int lotteryNumber = lotteryNumLong != null ? lotteryNumLong.intValue() : 0;
                Timestamp joinTime = doc.getTimestamp("joinTime");

                // Guard against incomplete documents
                if (eventID == null || status == null) {
                    Log.w("RDS", "Skipping malformed waitlist entry for user: " + userID);
                    continue;
                }

                WaitlistEntry entry = new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime);
                history.add(entry);
            }
            return history;

        } catch (Exception e) {
            Log.e("RDS", "query failed", e);
        }

        return new ArrayList<>(); // return empty list instead of null to prevent NPE downstream
    }

    /**
     * Async method to get waitlist for a specific event.
     * Used by UI components that need async operations.
     */
    public Task<QuerySnapshot> getWaitlist(String eventId) {
        return getCollectionRef(eventId).get();
    }
}
