package com.example.abacus_app;

import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private final MutableLiveData<List<User>>          searchResults = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<User>>          coOrganizers = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              error     = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>             lotteryCompleted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean>             eventDeleted = new MutableLiveData<>(false);
    private final MutableLiveData<Event>               eventDetails = new MutableLiveData<>();

    public ManageEventViewModel() {
        this.registrationRepository = new RegistrationRepository();
        this.notificationRepository = new NotificationRepository();
        this.eventRepository = new EventRepository();
    }

    public LiveData<List<WaitlistEntry>> getEntrants()  { return entrants; }
    public LiveData<List<Event>>         getEvents()    { return events; }
    public LiveData<List<User>>          getSearchResults() { return searchResults; }
    public LiveData<List<User>>          getCoOrganizers() { return coOrganizers; }
    public LiveData<String>              getError()     { return error; }
    public LiveData<Boolean>             getIsLoading() { return isLoading; }
    public LiveData<Boolean>             getLotteryCompleted() { return lotteryCompleted; }
    public LiveData<Boolean>             getEventDeleted() { return eventDeleted; }
    public LiveData<Event>               getEventDetails() { return eventDetails; }

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
                eventDetails.setValue(event);
                lotteryCompleted.setValue(event.isLotteryDrawn());
            } else {
                error.setValue("Failed to load event details");
            }
            isLoading.setValue(false);
        });
    }

    public void loadOrganizerEvents(String organizerId) {
        isLoading.setValue(true);
        eventRepository.getEventsByOrganizer(organizerId).addOnSuccessListener(queryDocumentSnapshots -> {
            List<Event> allEvents = queryDocumentSnapshots.toObjects(Event.class);
            // Filter out deleted events
            List<Event> activeEvents = allEvents.stream()
                    .filter(event -> !Boolean.TRUE.equals(event.getIsDeleted()))
                    .collect(Collectors.toList());
            events.setValue(activeEvents);
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

    public void searchUsersByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            searchResults.setValue(new ArrayList<>());
            return;
        }

        db.collection("users")
                .whereEqualTo("email", email.trim())
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<User> users = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        User user = doc.toObject(User.class);
                        user.setUid(doc.getId());
                        users.add(user);
                    }
                    searchResults.setValue(users);
                })
                .addOnFailureListener(e -> {
                    error.setValue("Search failed: " + e.getMessage());
                });
    }

    public void sendCoOrganizerInvite(String eventId, String eventTitle, User user) {
        if (eventId == null || user == null) return;

        Notification notification = new Notification(
                user.getUid(),
                user.getEmail(),
                eventId,
                "You have been invited to be a co-organizer for the event: " + eventTitle,
                "CO_ORGANIZER_INVITE"
        );
        
        db.collection("notifications")
                .add(notification)
                .addOnSuccessListener(documentReference -> {
                    searchResults.setValue(new ArrayList<>());
                })
                .addOnFailureListener(e -> {
                    error.setValue("Failed to send invite: " + e.getMessage());
                });
    }

    public void inviteToPrivateEvent(String eventId, String eventTitle, User user) {
        if (eventId == null || user == null) return;
        isLoading.setValue(true);
        registrationRepository.manuallyInviteEntrant(user.getUid(), eventId, e -> {
            isLoading.setValue(false);
            if (e != null) {
                error.setValue("Failed to invite: " + e.getMessage());
            } else {
                Notification notification = new Notification(
                        user.getUid(),
                        user.getEmail(),
                        eventId,
                        "You have been invited to the private event: " + eventTitle,
                        Notification.TYPE_SELECTED
                );
                db.collection("notifications").add(notification);
            }
        });
    }

    public void addCoOrganizer(String eventId, String userId) {
        if (eventId == null || userId == null) return;

        db.collection("events").document(eventId)
                .update("coOrganizers", FieldValue.arrayUnion(userId))
                .addOnSuccessListener(aVoid -> {
                    // Remove from waitlist if exists
                    registrationRepository.leaveWaitlist(userId, eventId, e -> {
                        if (e != null) {
                            Log.d(TAG, "User was not on waitlist or error removing: " + e.getMessage());
                        }
                        // Refresh co-organizers and waitlist
                        loadCoOrganizers(eventId);
                        loadWaitlist(eventId);
                    });
                })
                .addOnFailureListener(e -> {
                    error.setValue("Failed to add co-organizer: " + e.getMessage());
                });
    }

    public void loadCoOrganizers(String eventId) {
        db.collection("events").document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    Event event = documentSnapshot.toObject(Event.class);
                    if (event != null && event.getCoOrganizers() != null && !event.getCoOrganizers().isEmpty()) {
                        db.collection("users")
                                .whereIn(FieldPath.documentId(), event.getCoOrganizers())
                                .get()
                                .addOnSuccessListener(queryDocumentSnapshots -> {
                                    List<User> users = new ArrayList<>();
                                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                        User user = doc.toObject(User.class);
                                        user.setUid(doc.getId());
                                        users.add(user);
                                    }
                                    coOrganizers.setValue(users);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error loading co-organizer users", e);
                                    coOrganizers.setValue(new ArrayList<>());
                                });
                    } else {
                        coOrganizers.setValue(new ArrayList<>());
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading event for co-organizers", e);
                    coOrganizers.setValue(new ArrayList<>());
                });
    }
}