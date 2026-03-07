package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * Centralized data access layer for event data.
 * Owner: Himesh
 */
public class EventRepository {
    private final EventRemoteDataSource remoteDataSource;

    public EventRepository() {
        this.remoteDataSource = new EventRemoteDataSource();
    }

    public Task<Void> createEvent(Event event) {
        return remoteDataSource.createEvent(event);
    }

    public Task<QuerySnapshot> getEvents() {
        return remoteDataSource.getAllEvents();
    }

    public Task<DocumentSnapshot> getEventById(String eventId) {
        return remoteDataSource.getEventById(eventId);
    }

    public Task<Void> updateEvent(Event event) {
        return remoteDataSource.updateEvent(event);
    }

    public Task<Void> deleteEvent(String eventId) {
        return remoteDataSource.deleteEvent(eventId);
    }
}
