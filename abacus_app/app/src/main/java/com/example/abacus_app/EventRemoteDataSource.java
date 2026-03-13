package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * Handles direct Firestore communication for the events collection.
 * This class interacts with the "events" collection in Firebase Firestore to perform CRUD operations.
 * 
 * @author Himesh
 * @version 1.0
 */
public class EventRemoteDataSource {
    private final FirebaseFirestore db;
    private final CollectionReference eventsRef;

    /**
     * Initializes the Firestore instance and the events collection reference.
     */
    public EventRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
        eventsRef = db.collection("events");
    }

    /**
     * Creates a new event document in the "events" collection.
     * Assigns a unique ID to the event before saving.
     * 
     * @param event The {@link Event} object to be saved.
     * @return A {@link Task} representing the asynchronous Firestore operation.
     */
    public Task<Void> createEvent(Event event) {
        String id = eventsRef.document().getId();
        event.setEventId(id);
        return eventsRef.document(id).set(event);
    }

    /**
     * Retrieves a specific event document by its ID.
     * 
     * @param eventId The unique ID of the event to fetch.
     * @return A {@link Task} containing the {@link DocumentSnapshot} of the event.
     */
    public Task<DocumentSnapshot> getEventById(String eventId) {
        return eventsRef.document(eventId).get();
    }

    /**
     * Fetches all event documents from the "events" collection.
     * 
     * @return A {@link Task} containing the {@link QuerySnapshot} of all events.
     */
    public Task<QuerySnapshot> getAllEvents() {
        return eventsRef.get();
    }

    /**
     * Updates an existing event document in Firestore.
     * 
     * @param event The {@link Event} object with updated data.
     * @return A {@link Task} representing the asynchronous Firestore operation.
     */
    public Task<Void> updateEvent(Event event) {
        return eventsRef.document(event.getEventId()).set(event);
    }

    /**
     * Deletes an event document from Firestore.
     * 
     * @param eventId The unique ID of the event to delete.
     * @return A {@link Task} representing the asynchronous Firestore operation.
     */
    public Task<Void> deleteEvent(String eventId) {
        return eventsRef.document(eventId).delete();
    }
}
