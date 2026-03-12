package com.example.abacus_app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages UI state and business logic for the event creation process.
 * Owner: Himesh
 *
 * Poster images are stored as external URLs provided by the organizer.
 * No Firebase Storage upload is required.
 * QR codes are generated on-device in EventQrFragment from the eventId.
 */
public class CreateEventViewModel extends ViewModel {

    private final EventRepository eventRepository;

    private final MutableLiveData<Boolean> isSaving     = new MutableLiveData<>(false);
    private final MutableLiveData<String>  error        = new MutableLiveData<>();
    private final MutableLiveData<Boolean> eventCreated = new MutableLiveData<>(false);

    /**
     * Constructs a new CreateEventViewModel and initializes repositories.
     */
    public CreateEventViewModel() {
        this.eventRepository = new EventRepository();
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
     * Creates an event in Firestore. If a poster URL is provided it is
     * patched into the document immediately after creation using a Map
     * (avoids re-serializing the full POJO which can corrupt Timestamp fields).
     *
     * @param event     The event to create.
     * @param posterUrl External image URL string, or empty/null for no poster.
     */
    public void createEvent(Event event, String posterUrl) {
        isSaving.setValue(true);

        eventRepository.createEvent(event)
                .addOnFailureListener(e -> {
                    isSaving.setValue(false);
                    error.setValue("Failed to create event: " + e.getMessage());
                })
                .addOnSuccessListener(aVoid -> {
                    String eventId = event.getEventId();

                    if (posterUrl != null && !posterUrl.isEmpty()) {
                        patchPosterUrl(eventId, posterUrl);
                    } else {
                        isSaving.setValue(false);
                        eventCreated.setValue(true);
                    }
                });
    }

    /**
     * Patches only posterImageUrl into Firestore using a Map.
     * Avoids re-serializing the full Event POJO which can corrupt Timestamp fields.
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