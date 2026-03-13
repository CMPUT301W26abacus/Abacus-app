package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Centralized data access layer for event data.
 * Owner: Himesh
 */
public class EventRepository {

    private final EventRemoteDataSource remoteDataSource;
    private final ExecutorService executor    = Executors.newSingleThreadExecutor();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    public EventRepository() {
        this.remoteDataSource = new EventRemoteDataSource();
    }

    public Task<Void> createEvent(Event event) {
        return remoteDataSource.createEvent(event);
    }

    public Task<QuerySnapshot> getEvents() {
        return remoteDataSource.getAllEvents();
    }

    public Task<QuerySnapshot> getEventsByOrganizer(String organizerId) {
        return remoteDataSource.getEventsByOrganizer(organizerId);
    }

    /** Async callback version — use from UI/ViewModel. */
    public void getEventByIdAsync(String eventId, EventCallback callback) {
        executor.submit(() -> {
            try {
                Event event = remoteDataSource.getEventById(eventId);
                mainHandler.post(() -> callback.onResult(event));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    public Task<Void> updateEvent(Event event) {
        return remoteDataSource.updateEvent(event);
    }

    public Task<Void> deleteEvent(String eventId) {
        return remoteDataSource.deleteEvent(eventId);
    }

    public interface EventCallback {
        void onResult(Event event);
    }
}