package com.example.abacus_app;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.*;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

/**
 * Performs CRUD operations on the waitlist data in remote Firestore database.
 *
 * NOTE: The methods in this class run SYNCHRONOUSLY and are only to be used in the architecture
 * layer (repositories). For methods related to UI, refer to {@link RegistrationRepository}.
 *
 * @author Team Abacus
 * @version 1.0
 */
public class RegistrationRemoteDataSource {

    private final FirebaseFirestore firestore;

    public RegistrationRemoteDataSource() {
        firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference getCollectionRef(String eventID) {
        return firestore
                .collection("events")
                .document(eventID)
                .collection("waitlist");
    }

    public boolean isUserOnWaitlistSync(String userID, String eventID) throws Exception {

        DocumentSnapshot doc = Tasks.await(
                firestore.collection("events")
                        .document(userID)
                        .collection("waitlist")
                        .document(eventID)
                        .get()
        );

        return doc.exists();
    }

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

    public int getWaitlistSizeSync(String eventID) throws Exception {

        Log.d("mytagRDS", "Running from RDS...");

        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef(eventID).get(Source.SERVER)
        );

        return snapshot.size();
    }

    public void joinWaitlistSync(String eventID, WaitlistEntry entry) throws Exception {

        DocumentReference docRef =
                getCollectionRef(eventID)
                        .document(entry.getUserID());

        Tasks.await(docRef.set(entry));
    }

    public void removeWaitlistEntrySync(String eventId, String userId) throws Exception {

        DocumentReference docRef =
                getCollectionRef(eventId)
                        .document(userId);

        Tasks.await(docRef.delete());
    }

    public void updateUserEntryStatusSync(String eventId, String userId, String status) throws Exception {

        DocumentReference docRef =
                getCollectionRef(eventId)
                        .document(userId);

        Tasks.await(docRef.update("status", status));
    }

    public ArrayList<WaitlistEntry> getEntriesSync(String eventID) throws Exception {

        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef(eventID).get()
        );

        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String userID = doc.getString("userID");
            String status = doc.getString("status");
            Long lotteryNumberLong = doc.getLong("lotteryNumber");
            int lotteryNumber = lotteryNumberLong != null ? lotteryNumberLong.intValue() : 0;
            Timestamp joinTime = doc.getTimestamp("joinTime");

            WaitlistEntry entry = new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime);
            waitlist.add(entry);
        }

        return waitlist;
    }

    public ArrayList<WaitlistEntry> getEntriesWithStatusSync(String eventID, String status) throws Exception {

        QuerySnapshot snapshot = Tasks.await(
                getCollectionRef(eventID).get()
        );

        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();

        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            if (doc.getString("status").equals(status)) {
                String userID = doc.getString("userID");
                Long lotteryNumberLong = doc.getLong("lotteryNumber");
                int lotteryNumber = lotteryNumberLong != null ? lotteryNumberLong.intValue() : 0;
                Timestamp joinTime = doc.getTimestamp("joinTime");

                WaitlistEntry entry = new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime);
                waitlist.add(entry);
            }
        }

        return waitlist;
    }

    public ArrayList<WaitlistEntry> getHistoryForUserSync(String userID) throws ExecutionException, InterruptedException {

        try {
            QuerySnapshot snapshot = Tasks.await(
                    firestore.collectionGroup("waitlist")
                            .whereEqualTo("userID", userID)
                            .get()
            );

            ArrayList<WaitlistEntry> history = new ArrayList<>();

            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                String eventID = doc.getString("eventID");
                String status = doc.getString("status");
                Long lotteryNumLong = doc.getLong("lotteryNumber");
                int lotteryNumber = lotteryNumLong != null ? lotteryNumLong.intValue() : 0;
                Timestamp joinTime = doc.getTimestamp("joinTime");

                WaitlistEntry entry = new WaitlistEntry(userID, eventID, status, lotteryNumber, joinTime);
                history.add(entry);
            }

            return history;

        } catch (Exception e) {
            Log.e("RDS", "query failed", e);
        }

        return null;
    }
}
