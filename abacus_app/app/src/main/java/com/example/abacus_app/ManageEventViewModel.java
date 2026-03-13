package com.example.abacus_app;

import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages UI state and business logic for tracking entrants and finalizing the lottery.
 */
public class ManageEventViewModel extends ViewModel {

    private static final String TAG = "ManageEventViewModel";
    private final RegistrationRepository registrationRepository;
    private final NotificationRepository notificationRepository;
    private final EventRepository eventRepository;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    private final MutableLiveData<List<WaitlistEntry>> entrants  = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Event>>         events    = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              error     = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>             lotteryCompleted = new MutableLiveData<>(false);

    public ManageEventViewModel() {
        this.registrationRepository = new RegistrationRepository();
        this.notificationRepository = new NotificationRepository();
        this.eventRepository = new EventRepository();
    }

    public LiveData<List<WaitlistEntry>> getEntrants()  { return entrants; }
    public LiveData<List<Event>>         getEvents()    { return events; }
    public LiveData<String>              getError()     { return error; }
    public LiveData<Boolean>             getIsLoading() { return isLoading; }
    public LiveData<Boolean>             getLotteryCompleted() { return lotteryCompleted; }

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

    public void loadOrganizerEvents(String organizerId) {
        isLoading.setValue(true);
        eventRepository.getEventsByOrganizer(organizerId).addOnSuccessListener(queryDocumentSnapshots -> {
            List<Event> eventList = queryDocumentSnapshots.toObjects(Event.class);
            events.setValue(eventList);
            isLoading.setValue(false);
        }).addOnFailureListener(e -> {
            error.setValue("Failed to load events: " + e.getMessage());
            isLoading.setValue(false);
        });
    }

    /**
     * Performs the lottery draw using eventCapacity and waitlistCount from the event.
     * Updates users collection with "winner" or "loser" status.
     */
    public void drawLottery(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            error.setValue("Invalid Event ID");
            return;
        }

        isLoading.setValue(true);
        Log.d(TAG, "Starting lottery for event: " + eventId);

        // 1. Get Event Capacity from the event document
        db.collection("events").document(eventId).get().addOnSuccessListener(eventDoc -> {
            if (!eventDoc.exists()) {
                error.setValue("Event not found");
                isLoading.setValue(false);
                return;
            }

            Event event = eventDoc.toObject(Event.class);
            if (event == null) {
                error.setValue("Error reading event data");
                isLoading.setValue(false);
                return;
            }

            int capacity = (event.getEventCapacity() != null) ? event.getEventCapacity() : 0;
            Log.d(TAG, "Event capacity found: " + capacity);

            // 2. Get all waitlisted users for this event
            registrationRepository.getWaitlisted(eventId, waitlist -> {
                if (waitlist == null || waitlist.isEmpty()) {
                    error.setValue("No waitlisted users found for this event");
                    isLoading.setValue(false);
                    return;
                }

                // 3. Shuffle and pick winners
                Collections.shuffle(waitlist);
                int numToPick = Math.min(waitlist.size(), capacity);
                
                List<WaitlistEntry> winners = waitlist.subList(0, numToPick);
                List<WaitlistEntry> losers = waitlist.subList(numToPick, waitlist.size());

                // 4. Update status in Users and Registrations collections
                updateLotteryStatuses(eventId, winners, losers);
            });

        }).addOnFailureListener(e -> {
            error.setValue("Failed to fetch event: " + e.getMessage());
            isLoading.setValue(false);
        });
    }

    private void updateLotteryStatuses(String eventId, List<WaitlistEntry> winners, List<WaitlistEntry> losers) {
        WriteBatch batch = db.batch();

        List<String> winnerIds = new ArrayList<>();
        List<String> loserIds = new ArrayList<>();

        // Process Winners
        for (WaitlistEntry winner : winners) {
            String uid = winner.getUserId();
            winnerIds.add(uid);
            
            // Update User status
            batch.update(db.collection("users").document(uid), "status", "winner");
            
            // Update Registration status (to "invited" as per system flow)
            batch.update(db.collection("registrations").document(uid + "_" + eventId), "status", "invited");
        }

        // Process Losers
        for (WaitlistEntry loser : losers) {
            String uid = loser.getUserId();
            loserIds.add(uid);
            
            // Update User status
            batch.update(db.collection("users").document(uid), "status", "loser");
            
            // Keep registration as waitlisted or update to "not_selected" if preferred
            // We'll keep it waitlisted so they can be re-drawn if winners decline
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Log.i(TAG, "Lottery statuses updated successfully.");
            
            // 5. Send notifications
            if (!winnerIds.isEmpty()) notificationRepository.notifySelected(eventId, winnerIds);
            if (!loserIds.isEmpty()) notificationRepository.notifyNotSelected(eventId, loserIds);

            lotteryCompleted.setValue(true);
            isLoading.setValue(false);
            loadWaitlist(eventId); // Refresh the UI list
        }).addOnFailureListener(e -> {
            error.setValue("Failed to update lottery results: " + e.getMessage());
            isLoading.setValue(false);
        });
    }
}