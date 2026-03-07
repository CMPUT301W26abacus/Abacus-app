package com.example.abacus_app;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Manages UI state and business logic for the event creation process.
 * Owner: Himesh
 */
public class CreateEventViewModel extends ViewModel {
    private final EventRepository eventRepository;
    private final StorageRepository storageRepository;

    private final MutableLiveData<Boolean> isSaving = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> eventCreated = new MutableLiveData<>(false);

    public CreateEventViewModel() {
        this.eventRepository = new EventRepository();
        this.storageRepository = new StorageRepository();
    }

    public LiveData<Boolean> getIsSaving() { return isSaving; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getEventCreated() { return eventCreated; }

    /**
     * Creates an event, uploads the poster, and generates/uploads the QR code.
     */
    public void createEvent(Event event, Uri posterUri) {
        isSaving.setValue(true);
        
        // 1. Create the event doc first to ensure we have a valid ID for storage paths
        eventRepository.createEvent(event).addOnSuccessListener(aVoid -> {
            // Generate QR Code immediately after ID creation
            Bitmap qrBitmap = QRCodeGenerator.generateQRCode(event.getEventId(), 512);
            
            if (qrBitmap != null) {
                // Upload QR and potentially poster
                storageRepository.uploadQRCode(event.getEventId(), qrBitmap).addOnSuccessListener(qrTask -> {
                    storageRepository.getQRCodeUrl(event.getEventId()).addOnSuccessListener(qrUrl -> {
                        event.setQrCodeUrl(qrUrl.toString());
                        
                        if (posterUri != null) {
                            uploadPoster(event.getEventId(), posterUri, event);
                        } else {
                            // Final update if no poster
                            finalizeEvent(event);
                        }
                    });
                });
            } else if (posterUri != null) {
                uploadPoster(event.getEventId(), posterUri, event);
            } else {
                isSaving.setValue(false);
                eventCreated.setValue(true);
            }
        }).addOnFailureListener(e -> {
            isSaving.setValue(false);
            error.setValue("Failed to create event: " + e.getMessage());
        });
    }

    private void uploadPoster(String eventId, Uri uri, Event event) {
        storageRepository.uploadPoster(eventId, uri).addOnSuccessListener(taskSnapshot -> {
            storageRepository.getPosterUrl(eventId).addOnSuccessListener(downloadUri -> {
                event.setPosterImageUrl(downloadUri.toString());
                finalizeEvent(event);
            });
        }).addOnFailureListener(e -> {
            isSaving.setValue(false);
            error.setValue("Poster upload failed: " + e.getMessage());
        });
    }

    private void finalizeEvent(Event event) {
        eventRepository.updateEvent(event).addOnSuccessListener(v -> {
            isSaving.setValue(false);
            eventCreated.setValue(true);
        }).addOnFailureListener(e -> {
            isSaving.setValue(false);
            error.setValue("Final update failed: " + e.getMessage());
        });
    }
}
