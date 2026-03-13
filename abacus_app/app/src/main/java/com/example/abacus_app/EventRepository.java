package com.example.abacus_app;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

/**
 * Centralized data access layer for event data.
 * This repository handles all event-related operations by delegating to {@link EventRemoteDataSource}.
 * 
 * @author Himesh
 * @version 1.0
 */
public class EventRepository {
    private final EventRemoteDataSource remoteDataSource;

    /**
     * Initializes the repository with a default {@link EventRemoteDataSource}.
     */
    public EventRepository() {
        this.remoteDataSource = new EventRemoteDataSource();
    }

    /**
     * Creates a new event record in the database.
     * 
     * @param event The {@link Event} object to be created.
     * @return A {@link Task} representing the asynchronous operation.
     */
    public Task<Void> createEvent(Event event) {
        return remoteDataSource.createEvent(event);
    }

    /**
     * Fetches all events from the database.
     * 
     * @return A {@link Task} containing the {@link QuerySnapshot} of all events.
     */
    public Task<QuerySnapshot> getEvents() {
        return remoteDataSource.getAllEvents();
    }

    /**
     * Retrieves a specific event's details by its ID.
     * 
     * @param eventId The unique ID of the event.
     * @return A {@link Task} containing the {@link DocumentSnapshot} of the event.
     */
    public Task<DocumentSnapshot> getEventById(String eventId) {
        return remoteDataSource.getEventById(eventId);
    }

    /**
     * Updates an existing event record in the database.
     * 
     * @param event The {@link Event} object with updated information.
     * @return A {@link Task} representing the asynchronous operation.
     */
    public Task<Void> updateEvent(Event event) {
        return remoteDataSource.updateEvent(event);
    }

    /**
     * Deletes an event from the database.
     * 
     * @param eventId The unique ID of the event to delete.
     * @return A {@link Task} representing the asynchronous operation.
     */
    public Task<Void> deleteEvent(String eventId) {
        return remoteDataSource.deleteEvent(eventId);
    }
}
