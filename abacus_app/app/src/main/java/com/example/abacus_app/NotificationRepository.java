package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * NotificationRepository.java
 *
 * Handles creating and sending notifications for events.
 * Supports:
 *  - Selected / Not Selected notifications for users
 *  - Lottery results notifications
 *  - Replacement notifications
 *  - Listening for notifications by email
 *
 * Uses both asynchronous user lookup via UserRemoteDataSource and
 * executor-based synchronous notification processing for batch operations.
 */
public class NotificationRepository {

    private static final String TAG = "NotificationRepository";

    private final NotificationRemoteDataSource remote;
    private final UserRemoteDataSource userRemote;
    private final EventRemoteDataSource eventRemote;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationRepository() {
        this.remote = new NotificationRemoteDataSource();
        this.userRemote = new UserRemoteDataSource(com.google.firebase.firestore.FirebaseFirestore.getInstance());
        this.eventRemote = new EventRemoteDataSource();
    }

    // ── Selected / Not Selected Notifications ────────────────────────────────

    /**
     * Notify users they have been selected for an event.
     */
    public void notifySelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        executor.submit(() -> {
            try {
                Event event = eventRemote.getEventById(eventId);
                String organizerId = (event != null) ? event.getOrganizerId() : null;
                for (String userId : userIds) {
                    try {
                        User user = userRemote.getUserSync(userId);
                        if (user != null) {
                            Notification notification = new Notification(
                                    userId,
                                    user.getEmail(),
                                    organizerId,
                                    eventId,
                                    "Congratulations! You have been selected for the event.",
                                    Notification.TYPE_SELECTED
                            );
                            remote.saveNotification(notification);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying user: " + userId, e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in notifySelected", e);
            }
        });
    }

    /**
     * Notify users they were not selected for an event.
     */
    public void notifyNotSelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        executor.submit(() -> {
            try {
                Event event = eventRemote.getEventById(eventId);
                String organizerId = (event != null) ? event.getOrganizerId() : null;
                for (String userId : userIds) {
                    try {
                        User user = userRemote.getUserSync(userId);
                        if (user != null) {
                            Notification notification = new Notification(
                                    userId,
                                    user.getEmail(),
                                    organizerId,
                                    eventId,
                                    "We regret to inform you that you were not selected for the event this time.",
                                    Notification.TYPE_NOT_SELECTED
                            );
                            remote.saveNotification(notification);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying user: " + userId, e);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in notifyNotSelected", e);
            }
        });
    }

    // ── Listening ───────────────────────────────────────────────────────────

    /**
     * Listen for notifications filtered by user email.
     */
    public void listenForNotificationsByEmail(String email, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotificationsByEmail(email, listener);
    }

    // ── Lottery & Replacement Notifications ──────────────────────────────────

    /**
     * Notify all users when lottery results are drawn for an event.
     */
    public void notifyLotteryResults(String eventId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                RegistrationRemoteDataSource registrationRDS = new RegistrationRemoteDataSource();
                ArrayList<WaitlistEntry> entries = registrationRDS.getEntriesSync(eventId);
                Event event = eventRemote.getEventById(eventId);
                String organizerId = (event != null) ? event.getOrganizerId() : null;

                for (WaitlistEntry entry : entries) {
                    String userId = entry.getUserId();
                    try {
                        User user = userRemote.getUserSync(userId);
                        if (user != null) {
                            Notification notification;
                            if (entry.getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
                                notification = new Notification(
                                        userId,
                                        user.getEmail(),
                                        organizerId,
                                        eventId,
                                        "Congratulations! You have been invited to " + (event != null ? event.getTitle() : "the event"),
                                        Notification.TYPE_SELECTED
                                );
                            } else { // waitlisted
                                notification = new Notification(
                                        userId,
                                        user.getEmail(),
                                        organizerId,
                                        eventId,
                                        "The lottery for " + (event != null ? event.getTitle() : "the event") + " has been drawn. Unfortunately you have not been selected at this time.",
                                        Notification.TYPE_NOT_SELECTED
                                );
                            }
                            remote.saveNotification(notification);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error notifying user: " + userId, e);
                    }
                }

                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Notify a user as a replacement for an event.
     */
    public void notifyReplacement(String eventId, String userId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                Event event = eventRemote.getEventById(eventId);
                String organizerId = (event != null) ? event.getOrganizerId() : null;
                User user = userRemote.getUserSync(userId);
                if (user != null) {
                    Notification notification = new Notification(
                            userId,
                            user.getEmail(),
                            organizerId,
                            eventId,
                            "Congratulations! You have been invited to " + (event != null ? event.getTitle() : "the event"),
                            Notification.TYPE_SELECTED
                    );
                    remote.saveNotification(notification);
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(e));
                }
            }
        });
    }

    /**
     * Notify a user that they have been cancelled from an event.
     */
    public void notifyCancelled(String eventId, String userId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                Event event = eventRemote.getEventById(eventId);
                String organizerId = (event != null) ? event.getOrganizerId() : null;
                User user = userRemote.getUserSync(userId);
                if (user != null) {
                    Notification notification = new Notification(
                            userId,
                            user.getEmail(),
                            organizerId,
                            eventId,
                            (event != null ? event.getTitle() : "the event") + " has been cancelled.",
                            Notification.TYPE_CANCELED
                    );
                    remote.saveNotification(notification);
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(e));
                }
            }
        });
    }

    // ── Callback Interface ───────────────────────────────────────────────────

    /**
     * Generic callback for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
