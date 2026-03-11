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
 * Owner: Himesh
 */
public class ManageEventViewModel extends ViewModel {

    private final RegistrationRemoteDataSource registrationDataSource;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<WaitlistEntry>> entrants  = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>>         events    = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              error     = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading = new MutableLiveData<>(false);

    public ManageEventViewModel() {
        this.registrationDataSource = new RegistrationRemoteDataSource();
    }

    public LiveData<List<WaitlistEntry>> getEntrants()  { return entrants; }
    public LiveData<List<Event>>         getEvents()    { return events; }
    public LiveData<String>              getError()     { return error; }
    public LiveData<Boolean>             getIsLoading() { return isLoading; }

    /** Loads all events for the given organizer UUID. */
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

    /** Loads the waitlist for a specific event. */
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

    public void cancelEntrant(String eventId, String userId) {
        // US 02.06.04: implementation pending
    }

    public void exportToCSV(List<WaitlistEntry> entries) {
        // US 02.06.05: implementation pending
    }
}