package com.example.abacus_app;

import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
 * ViewModel for the organizer manage-event screen.
 *
 * <p>Owns all UI state and business logic for:
 * <ul>
 *   <li>Loading and displaying an organizer's events (by device UUID and/or Firebase UID)</li>
 *   <li>Viewing and filtering the waitlist / entrant list for a specific event</li>
 *   <li>Running and replacing lottery draws</li>
 *   <li>Deleting events</li>
 *   <li>Searching users and managing co-organizers</li>
 * </ul>
 *
 * <p>All Firestore operations are asynchronous. Observe the exposed {@link LiveData} fields
 * from a Fragment or Activity to react to state changes.
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

    /**
     * Loads all waitlist entries for the given event and posts the result to {@link #getEntrants()}.
     *
     * @param eventId Firestore document ID of the event whose waitlist to load.
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
     * Fetches the event document and posts it to {@link #getEventDetails()} and
     * {@link #getLotteryCompleted()}.
     *
     * @param eventId Firestore document ID of the event.
     */
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

    /**
     * Loads events owned by the given organizer, matching on device UUID only.
     * Convenience overload for callers that do not have a Firebase UID.
     *
     * @param organizerId Device UUID of the organizer.
     */
    public void loadOrganizerEvents(String organizerId) {
        loadOrganizerEvents(organizerId, null);
    }

    /**
     * Loads all non-deleted events where the organizer ID matches either
     * {@code deviceUuid} or {@code firebaseUid}.
     *
     * <p>Both parameters are optional, but at least one must be non-null.
     * If both are null, {@link #getError()} is updated with an error message and
     * the method returns early without making a Firestore query.
     *
     * @param deviceUuid  Device-local UUID of the organizer (may be null).
     * @param firebaseUid Firebase Auth UID of the organizer (may be null).
     */
    public void loadOrganizerEvents(String deviceUuid, String firebaseUid) {
        isLoading.setValue(true);
        java.util.ArrayList<String> organizerIds = new java.util.ArrayList<>();
        if (deviceUuid != null) organizerIds.add(deviceUuid);
        if (firebaseUid != null) organizerIds.add(firebaseUid);

        if (organizerIds.isEmpty()) {
            error.setValue("No organizer ID found");
            isLoading.setValue(false);
            return;
        }

        eventRepository.getEventsByOrganizerIds(organizerIds).addOnSuccessListener(queryDocumentSnapshots -> {
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

    /**
     * Soft-deletes the event with the given ID, then refreshes the organizer's event list.
     *
     * <p>On success, {@link #getEventDeleted()} is set to {@code true} and the event list is
     * reloaded using both {@code deviceUuid} and {@code firebaseUid} so that events owned under
     * either identity are shown correctly after deletion.
     *
     * @param eventId     Firestore document ID of the event to delete.
     * @param deviceUuid  Device UUID of the organizer, used to refresh the event list.
     * @param firebaseUid Firebase Auth UID of the organizer, used to refresh the event list.
     */
    public void deleteEvent(String eventId, String deviceUuid, String firebaseUid) {
        isLoading.setValue(true);
        eventRepository.deleteEvent(eventId).addOnSuccessListener(aVoid -> {
            eventDeleted.setValue(true);
            loadOrganizerEvents(deviceUuid, firebaseUid); // Refresh list with both UUIDs
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

                    notificationRepository.notifyReplacement(eventId, entry.getUserID(), new NotificationRepository.VoidCallback() {
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

    /**
     * Searches Firestore for users whose {@code email} field exactly matches the given string.
     * Results are posted to {@link #getSearchResults()}.
     *
     * @param email The email address to search for. Null or blank clears results immediately.
     */
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

    /**
     * Sends a co-organizer invite notification to the specified user for the given event.
     *
     * @param eventId    Firestore document ID of the event.
     * @param eventTitle Display title of the event, included in the notification message.
     * @param user       The user to invite. Must have a non-null UID and email.
     */
    public void sendCoOrganizerInvite(String eventId, String eventTitle, User user) {
        if (eventId == null || user == null) return;

        db.collection("events").document(eventId).get().addOnSuccessListener(documentSnapshot -> {
            String organizerId = documentSnapshot.getString("organizerId");
            Notification notification = new Notification(
                    user.getUid(),
                    user.getEmail(),
                    organizerId,
                    eventId,
                    "You have been invited to be a co-organizer for the event: " + eventTitle,
                    Notification.TYPE_CO_ORGANIZER_INVITE
            );

            db.collection("notifications")
                    .add(notification)
                    .addOnSuccessListener(documentReference -> {
                        searchResults.setValue(new ArrayList<>());
                    })
                    .addOnFailureListener(e -> {
                        error.setValue("Failed to send invite: " + e.getMessage());
                    });
        }).addOnFailureListener(e -> {
            error.setValue("Failed to fetch event for invitation: " + e.getMessage());
        });
    }

    /**
     * Manually registers a user onto a private event's waitlist and sends a
     * {@link Notification#TYPE_SELECTED} notification to that user.
     *
     * @param eventId    Firestore document ID of the private event.
     * @param eventTitle Display title of the event, included in the notification message.
     * @param user       The user to invite. Must have a non-null UID.
     */
    public void inviteToPrivateEvent(String eventId, String eventTitle, User user) {
        if (eventId == null || user == null) return;
        isLoading.setValue(true);
        registrationRepository.manuallyInviteEntrant(user.getUid(), eventId, e -> {
            isLoading.setValue(false);
            if (e != null) {
                error.setValue("Failed to invite: " + e.getMessage());
            } else {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                String organizerId = currentUser != null ? currentUser.getUid() : null;

                Notification notification = new Notification(
                        user.getUid(),
                        user.getEmail(),
                        organizerId,
                        eventId,
                        "You have been invited to the private event: " + eventTitle,
                        Notification.TYPE_SELECTED
                );
                db.collection("notifications").add(notification);
            }
        });
    }

    /**
     * Adds the user with {@code userId} to the {@code coOrganizers} array on the event document,
     * removes them from the waitlist if present, and refreshes both lists.
     *
     * @param eventId Firestore document ID of the event.
     * @param userId  Firebase UID of the user to promote to co-organizer.
     */
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

    /**
     * Fetches the full {@link User} objects for all co-organizers of the given event and
     * posts them to {@link #getCoOrganizers()}.
     *
     * @param eventId Firestore document ID of the event.
     */
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

    /**
     * Sends manual custom notifications to a list of users for a specific event.
     */
    public void sendManualNotifications(String eventId, List<String> userIds, String message) {
        if (eventId == null || userIds == null || userIds.isEmpty() || message == null || message.trim().isEmpty()) {
            return;
        }
        isLoading.setValue(true);
        notificationRepository.sendManualNotification(eventId, userIds, message, "MANUAL");
        isLoading.setValue(false);
    }
}
