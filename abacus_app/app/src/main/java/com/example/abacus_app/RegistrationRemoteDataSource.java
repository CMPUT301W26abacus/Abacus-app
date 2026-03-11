package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles all remote data operations (Firestore) for events and waitlists.
 */
public class RegistrationRemoteDataSource {

    private final FirebaseFirestore db;
    private final CollectionReference eventsRef;

    public RegistrationRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
        eventsRef = db.collection("events");
    }

    /**
     * Saves a new event to Firestore.
     */
    public Task<Void> createEvent(Event event) {
        String id = eventsRef.document().getId();
        event.setEventId(id);
        return eventsRef.document(id).set(event);
    }

    /**
     * Updates an existing event's details.
     */
    public Task<Void> updateEvent(Event event) {
        return eventsRef.document(event.getEventId()).set(event);
    }

    /**
     * Joins the waitlist for a specific event.
     */
    public void joinWaitlist(String eventID, WaitlistEntry entry) {
        eventsRef.document(eventID).collection("waitlist")
                .document(entry.getUserId()).set(entry);
    }

    /**
     * Updates the status field of a single waitlist entry.
     */
    public void updateStatus(String eventID, String userID, String status) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", status);
        eventsRef.document(eventID).collection("waitlist")
                .document(userID).update(update);
    }

    /**
     * Removes a user from the waitlist entirely.
     */
    public void deleteFromWaitlist(String eventID, String userID) {
        eventsRef.document(eventID).collection("waitlist")
                .document(userID).delete();
    }

    /**
     * Fetches all waitlisted entrants for a specific event.
     */
    public Task<QuerySnapshot> getWaitlist(String eventID) {
        return eventsRef.document(eventID).collection("waitlist").get();
    }
}