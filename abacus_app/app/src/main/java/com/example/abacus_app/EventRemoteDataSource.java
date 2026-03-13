package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.ExecutionException;

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

    public Event getEventById(String eventId) throws ExecutionException, InterruptedException {
        DocumentSnapshot doc = Tasks.await(eventsRef.document(eventId).get());
        if (doc.exists()) {
            return doc.toObject(Event.class);
        }
        return null;
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
