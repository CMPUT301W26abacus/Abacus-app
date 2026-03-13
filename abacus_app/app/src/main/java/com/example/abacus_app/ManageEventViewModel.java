package com.example.abacus_app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages UI state and business logic for tracking entrants and finalizing the lottery.
 * Responsible for loading event-specific waitlists and managing entrant status.
 * 
 * @author Himesh
 * @version 1.0
 */
public class ManageEventViewModel extends ViewModel {

    private final RegistrationRepository registrationRepository;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<WaitlistEntry>> entrants  = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>>         events    = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              error     = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading = new MutableLiveData<>(false);

    /**
     * Constructs the ViewModel and initializes the repository.
     */
    public ManageEventViewModel() {
        this.registrationRepository = new RegistrationRepository();
    }

    /** @return List of entrants currently on the waitlist. */
    public LiveData<List<WaitlistEntry>> getEntrants()  { return entrants; }

    /** @return List of events managed by the current organizer. */
    public LiveData<List<Event>>         getEvents()    { return events; }

    /** @return Any error message encountered during data operations. */
    public LiveData<String>              getError()     { return error; }

    /** @return Loading status indicator. */
    public LiveData<Boolean>             getIsLoading() { return isLoading; }

    /**
     * Loads all events for the given organizer UUID.
     * US 02.01.01 context: View events created by the organizer.
     *
     * @param organizerId The unique device/organizer ID.
     */
    public void loadOrganizerEvents(String organizerId) {
        isLoading.setValue(true);
        db.collection("events")
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) list.add(event);
                    }
                    events.setValue(list);
                    isLoading.setValue(false);
                })
                .addOnFailureListener(e -> {
                    isLoading.setValue(false);
                    error.setValue("Failed to load events: " + e.getMessage());
                });
    }

    /**
     * Loads the waitlist for a specific event.
     * US 02.02.01 implementation: View the list of entrants who joined the event waiting list.
     *
     * @param eventId The ID of the event to fetch the waitlist for.
     */
    public void loadWaitlist(String eventId) {
        isLoading.setValue(true);
        registrationRepository.getAllEntries(eventId, waitlist -> {
            if (waitlist != null) {
                entrants.setValue(new ArrayList<>(waitlist));
            } else {
                error.setValue("Failed to load waitlist");
            }
            isLoading.setValue(false);
        });
    }

    /**
     * Cancels an entrant's registration for an event.
     * US 02.06.04 implementation: Cancel entrants that did not sign up.
     *
     * @param eventId The event ID.
     * @param userId  The ID of the user to cancel.
     */
    public void cancelEntrant(String eventId, String userId) {
        registrationRepository.cancelEntrant(userId, eventId, err -> {
            if (err != null) {
                error.setValue("Failed to cancel entrant: " + err.getMessage());
            }
        });
    }

    /**
     * Formats and exports the final list of enrolled entrants to a CSV file.
     * US 02.06.05 implementation: Export final list of enrolled entrants to CSV.
     *
     * @param entries The list of waitlist entries to export.
     */
    public void exportToCSV(List<WaitlistEntry> entries) {
        // Implementation for CSV export pending (next milestone)
    }
}
