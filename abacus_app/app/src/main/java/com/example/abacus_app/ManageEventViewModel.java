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
 */
public class ManageEventViewModel extends ViewModel {

    private final RegistrationRemoteDataSource registrationDataSource;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<WaitlistEntry>> entrants  = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>>         events    = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              error     = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading = new MutableLiveData<>(false);

    /**
     * Constructs the ViewModel and initializes the remote data source.
     */
    public ManageEventViewModel() {
        this.registrationDataSource = new RegistrationRemoteDataSource();
    }

    /**
     * @return List of entrants currently on the waitlist.
     */
    public LiveData<List<WaitlistEntry>> getEntrants()  { return entrants; }
    
    /**
     * @return List of events managed by the current organizer.
     */
    public LiveData<List<Event>>         getEvents()    { return events; }
    
    /**
     * @return Any error message encountered during data operations.
     */
    public LiveData<String>              getError()     { return error; }
    
    /**
     * @return Loading status indicator.
     */
    public LiveData<Boolean>             getIsLoading() { return isLoading; }

    /** 
     * Loads all events for the given organizer UUID. 
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
     * 
     * @param eventId The ID of the event to fetch the waitlist for.
     */
    public void loadWaitlist(String eventId) {
        isLoading.setValue(true);
        registrationDataSource.getWaitlist(eventId).addOnSuccessListener(querySnapshot -> {
            List<WaitlistEntry> list = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot) {
                WaitlistEntry entry = doc.toObject(WaitlistEntry.class);
                if (entry != null) list.add(entry);
            }
            entrants.setValue(list);
            isLoading.setValue(false);
        }).addOnFailureListener(e -> {
            isLoading.setValue(false);
            error.setValue("Failed to load waitlist: " + e.getMessage());
        });
    }

    /**
     * Cancels an entrant's registration for an event.
     * US 02.06.04 implementation.
     * 
     * @param eventId The event ID.
     * @param userId The ID of the user to cancel.
     */
    public void cancelEntrant(String eventId, String userId) {
        registrationDataSource.updateStatus(eventId, userId, WaitlistEntry.STATUS_CANCELLED);
    }

    /**
     * Formats and exports the final list of enrolled entrants to a CSV file.
     * US 02.06.05 implementation.
     * 
     * @param entries The list of waitlist entries to export.
     */
    public void exportToCSV(List<WaitlistEntry> entries) {
        // Implementation for CSV export pending (next milestone)
    }
}
