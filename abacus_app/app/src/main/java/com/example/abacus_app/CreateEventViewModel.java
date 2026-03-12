package com.example.abacus_app;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages UI state and business logic for the event creation process.
 * Responsible for orchestrating Firestore document creation, QR code generation,
 * and Firebase Storage uploads for posters.
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
     * Creates an event in Firestore. If a poster Uri is provided, it is uploaded
     * to Firebase Storage. A unique QR code is also generated and uploaded.
     * Finally, both URLs are saved into the Firestore document.
     *
     * @param event      The event object containing metadata.
     * @param posterUri  Local Uri of the image to upload, or null for no poster.
     */
    public void createEvent(Event event, Uri posterUri) {
        isSaving.setValue(true);

        eventRepository.createEvent(event)
                .addOnFailureListener(e -> handleError("Failed to create event: " + e.getMessage()))
                .addOnSuccessListener(aVoid -> {
                    String eventId = event.getEventId();
                    generateAndUploadMedia(eventId, posterUri);
                });
    }

    /**
     * Generates a QR code and uploads it to storage.
     * 
     * @param eventId The ID of the event to link the QR code to.
     * @param posterUri Optional poster to upload after QR is done.
     */
    private void generateAndUploadMedia(String eventId, Uri posterUri) {
        try {
            // US 02.01.01: Generate QR Code from eventId
            BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
            Bitmap bitmap = barcodeEncoder.encodeBitmap(eventId, BarcodeFormat.QR_CODE, 400, 400);

            // Upload QR Code
            storageRepository.uploadQRCode(eventId, bitmap)
                    .addOnSuccessListener(taskSnapshot -> {
                        storageRepository.getQRCodeUrl(eventId)
                                .addOnSuccessListener(qrUri -> {
                                    if (posterUri != null) {
                                        uploadPosterAndFinalize(eventId, qrUri.toString(), posterUri);
                                    } else {
                                        patchMediaUrls(eventId, qrUri.toString(), null);
                                    }
                                })
                                .addOnFailureListener(e -> handleError("Failed to get QR URL: " + e.getMessage()));
                    })
                    .addOnFailureListener(e -> handleError("Failed to upload QR: " + e.getMessage()));

        } catch (Exception e) {
            handleError("QR Generation failed: " + e.getMessage());
        }
    }

    /**
     * Uploads the event poster and then updates the Firestore document.
     * 
     * @param eventId The event ID.
     * @param qrUrl The already generated QR URL.
     * @param posterUri The local Uri for the poster.
     */
    private void uploadPosterAndFinalize(String eventId, String qrUrl, Uri posterUri) {
        storageRepository.uploadPoster(eventId, posterUri)
                .addOnSuccessListener(taskSnapshot -> {
                    storageRepository.getPosterUrl(eventId)
                            .addOnSuccessListener(posterUriResult -> 
                                patchMediaUrls(eventId, qrUrl, posterUriResult.toString()))
                            .addOnFailureListener(e -> handleError("Failed to get poster URL: " + e.getMessage()));
                })
                .addOnFailureListener(e -> handleError("Failed to upload poster: " + e.getMessage()));
    }

    /**
     * Updates the event document with the generated media URLs using a Map.
     * 
     * @param eventId The ID of the document to update.
     * @param qrUrl The URL of the QR code image in Storage.
     * @param posterUrl The URL of the poster image in Storage (can be null).
     */
    private void patchMediaUrls(String eventId, String qrUrl, String posterUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("qrCodeUrl", qrUrl);
        if (posterUrl != null) {
            updates.put("posterImageUrl", posterUrl);
        }

        FirebaseFirestore.getInstance()
                .collection("events")
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(v -> {
                    isSaving.setValue(false);
                    eventCreated.setValue(true);
                })
                .addOnFailureListener(e -> handleError("Failed to save media URLs: " + e.getMessage()));
    }

    /**
     * Sets error state and stops loading indicator.
     * 
     * @param msg The error message to show.
     */
    private void handleError(String msg) {
        isSaving.setValue(false);
        error.setValue(msg);
    }
}
