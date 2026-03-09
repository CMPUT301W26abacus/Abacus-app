package com.example.abacus_app;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.*;

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

    public int getWaitlistSizeSync(String eventId) throws Exception {

        AggregateQuery query = getCollectionRef(eventId).count();

        AggregateQuerySnapshot snapshot =
                Tasks.await(query.get(AggregateSource.SERVER));

        return Math.toIntExact(snapshot.getCount());
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
}
