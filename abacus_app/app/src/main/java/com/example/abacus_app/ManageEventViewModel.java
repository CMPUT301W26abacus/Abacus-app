package com.example.abacus_app;

import android.util.Log;
import android.widget.Toast;

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
    private final MutableLiveData<Boolean>             eventDeleted = new MutableLiveData<>(false);

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
    public LiveData<Boolean>             getEventDeleted() { return eventDeleted; }

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

    public void loadLotteryStatus(String eventId) {
        isLoading.setValue(true);
        eventRepository.getEventByIdAsync(eventId, event -> {
            if (event != null) {
                lotteryCompleted.setValue(event.isLotteryDrawn());
            } else {
                error.setValue("Failed to load lottery status");
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

    public void deleteEvent(String eventId, String organizerId) {
        isLoading.setValue(true);
        eventRepository.deleteEvent(eventId).addOnSuccessListener(aVoid -> {
            eventDeleted.setValue(true);
            loadOrganizerEvents(organizerId); // Refresh list
        }).addOnFailureListener(e -> {
            error.setValue("Failed to delete event: " + e.getMessage());
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

        registrationRepository.runLottery(eventId, new RegistrationRepository.VoidCallback() {
            @Override
            public void onComplete(Exception error) {
                isLoading.setValue(false);
                if (error != null) {
                    Log.d(TAG, error.getMessage());
                } else {
                    lotteryCompleted.setValue(true);
                    loadWaitlist(eventId);
                    notificationRepository.notifyLotteryResults(eventId, new NotificationRepository.VoidCallback() {
                        @Override
                        public void onComplete(Exception error) {
                            if (error != null) {
                                Log.d(TAG, "onComplete: error sending notifications");
                            }
                        }
                    });
                }
            }
        });
    }

    public void drawReplacement(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            error.setValue("Invalid Event ID");
            return;
        }
        isLoading.setValue(true);
        registrationRepository.drawReplacement(eventId, new RegistrationRepository.EntryCallback() {
            @Override
            public void onResult(WaitlistEntry entry) {
                isLoading.setValue(false);
                if (entry == null) {
                    Log.d("mytagmanageeventVM", "onResult: draw replacement failed");
                } else {
                    Log.d("mytagmanageeventVM", "onResult: replacement drawn = " + entry.getUserId());
                    loadWaitlist(eventId);
                }
                notificationRepository.notifyReplacement(eventId, entry.getUserID(), new NotificationRepository.VoidCallback() {
                    @Override
                    public void onComplete(Exception error) {
                        if (error != null) {
                            Log.d(TAG, "onComplete: error sending notifications");
                        }
                    }
                });
            }
        });
    }
}