package com.example.abacus_app;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages UI state and business logic for the event creation process.
 * Handles event persistence and optional poster URL or Gallery image association.
 * 
 * US 02.01.04: Set a registration period.
 * US 02.03.01: Optionally limit the number of entrants.
 * US 02.04.01: Upload event poster (handled via URL patching or Firebase Storage).
 * 
 * @author Himesh
 * @version 1.1
 */
public class CreateEventViewModel extends ViewModel {

    private final EventRepository eventRepository;
    private final StorageRepository storageRepository;

    private final MutableLiveData<Boolean> isSaving     = new MutableLiveData<>(false);
    private final MutableLiveData<String>  error        = new MutableLiveData<>();
    private final MutableLiveData<Boolean> eventCreated = new MutableLiveData<>(false);

    /**
     * Constructs a new CreateEventViewModel and initializes repositories.
     */
    public CreateEventViewModel() {
        this.eventRepository = new EventRepository();
        this.storageRepository = new StorageRepository();
    }

    /**
     * @return Observable status of whether a save operation is in progress.
     */
    public LiveData<Boolean> getIsSaving()     { return isSaving; }

    /**
     * @return Observable error messages to be displayed in the UI.
     */
    public LiveData<String>  getError()        { return error; }

    /**
     * @return Observable trigger for successful event creation.
     */
    public LiveData<Boolean> getEventCreated() { return eventCreated; }

    /**
     * Creates an event in Firestore. If a poster URL or URI is provided, it is
     * associated with the document after creation.
     *
     * @param event      The {@link Event} object to create.
     * @param posterUrl  External image URL string (optional).
     * @param posterUri  Local Uri for gallery image (optional).
     */
    public void createEvent(Event event, String posterUrl, Uri posterUri) {
        isSaving.setValue(true);

        eventRepository.createEvent(event)
                .addOnFailureListener(e -> {
                    isSaving.setValue(false);
                    error.setValue("Failed to create event: " + e.getMessage());
                })
                .addOnSuccessListener(aVoid -> {
                    String eventId = event.getEventId();

                    if (posterUri != null) {
                        uploadPosterAndPatch(eventId, posterUri);
                    } else if (posterUrl != null && !posterUrl.isEmpty()) {
                        patchPosterUrl(eventId, posterUrl);
                    } else {
                        isSaving.setValue(false);
                        eventCreated.setValue(true);
                    }
                });
    }

    private void uploadPosterAndPatch(String eventId, Uri posterUri) {
        storageRepository.uploadPoster(eventId, posterUri)
                .addOnSuccessListener(taskSnapshot -> 
                    storageRepository.getPosterUrl(eventId)
                        .addOnSuccessListener(uri -> patchPosterUrl(eventId, uri.toString()))
                        .addOnFailureListener(e -> {
                            isSaving.setValue(false);
                            error.setValue("Failed to get download URL: " + e.getMessage());
                        })
                )
                .addOnFailureListener(e -> {
                    isSaving.setValue(false);
                    error.setValue("Failed to upload poster: " + e.getMessage());
                });
    }

    /**
     * Patches only the posterImageUrl into Firestore using a Map to avoid overwriting other fields.
     * 
     * @param eventId   The ID of the event to update.
     * @param posterUrl The URL of the poster image.
     */
    private void patchPosterUrl(String eventId, String posterUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("posterImageUrl", posterUrl);

        FirebaseFirestore.getInstance()
                .collection("events")
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    isSaving.setValue(false);
                    eventCreated.setValue(true);
                })
                .addOnFailureListener(e -> {
                    isSaving.setValue(false);
                    error.setValue("Failed to save poster URL: " + e.getMessage());
                });
    }
}
