package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * Handles direct Firestore communication for the events collection.
 * Owner: Himesh
 */
public class EventRemoteDataSource {
    private final FirebaseFirestore db;
    private final CollectionReference eventsRef;

    public EventRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
        eventsRef = db.collection("events");
    }

    public Task<Void> createEvent(Event event) {
        String id = eventsRef.document().getId();
        event.setEventId(id);
        return eventsRef.document(id).set(event);
    }

    public Task<DocumentSnapshot> getEventById(String eventId) {
        return eventsRef.document(eventId).get();
    }

    public Task<QuerySnapshot> getAllEvents() {
        return eventsRef.get();
    }

    public Task<QuerySnapshot> getEventsByOrganizer(String organizerId) {
        return eventsRef.whereEqualTo("organizerId", organizerId).get();
    }

    public Task<Void> updateEvent(Event event) {
        return eventsRef.document(event.getEventId()).set(event);
    }

    public Task<Void> deleteEvent(String eventId) {
        return eventsRef.document(eventId).delete();
    }
}
