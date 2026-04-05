package com.example.abacus_app;

import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * CreateEventViewModel.java
 *
 * Manages UI state and business logic for the event creation process.
 * Handles event persistence and image upload to Cloudinary.
 * 
 * US 02.04.01: Upload event poster.
 * 
 * @author Himesh
 * @version 1.4
 */
public class CreateEventViewModel extends ViewModel {

    private final EventRepository eventRepository;
    private final StorageRepository storageRepository;

    private final MutableLiveData<Boolean> isSaving     = new MutableLiveData<>(false);
    private final MutableLiveData<String>  error        = new MutableLiveData<>();
    private final MutableLiveData<Boolean> eventCreated = new MutableLiveData<>(false);

    public CreateEventViewModel() {
        this.eventRepository = new EventRepository();
        this.storageRepository = new StorageRepository();
    }

    public LiveData<Boolean> getIsSaving()     { return isSaving; }
    public LiveData<String>  getError()        { return error; }
    public LiveData<Boolean> getEventCreated() { return eventCreated; }

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

    /**
     * Uploads the poster to Cloudinary and retrieves the secure URL to 
     * patch into the Firestore event document.
     */
    private void uploadPosterAndPatch(String eventId, Uri posterUri) {
        storageRepository.uploadImage(posterUri, new StorageRepository.CloudinaryCallback() {
            @Override
            public void onSuccess(String url) {
                patchPosterUrl(eventId, url);
            }

            @Override
            public void onError(String errorMessage) {
                isSaving.setValue(false);
                error.setValue("Failed to upload poster: " + errorMessage);
            }
        });
    }

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
                    error.setValue("Failed to save poster URL to Firestore: " + e.getMessage());
                });
    }
}
