package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

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

        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            for (String userId : userIds) {
                userRemote.getUser(userId, user -> {
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
                });
            }
        });
    }

    /**
     * Notify users they were not selected for an event.
     */
    public void notifyNotSelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            for (String userId : userIds) {
                userRemote.getUser(userId, user -> {
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
                });
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
     * Sends notifications to winners and losers when the lottery of an event is drawn.
     *
     * @param eventId  the unique ID of the event in the database
     * @param callback called when the operation completes
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
                    userRemote.getUser(userId, user -> {
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
                    });
                }

                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Notifies a single user that they have been invited as a replacement for an event.
     *
     * @param eventId  the unique ID of the event in the database
     * @param userId   the unique ID of the user who was drawn
     * @param callback called when the operation completes
     */
    public void notifyReplacement(String eventId, String userId, VoidCallback callback) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            userRemote.getUser(userId, user -> {
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
            });
        });
    }

    /**
     * Notifies a single user that their invitation to an event has expired/been cancelled.
     *
     * @param eventId  the unique ID of the event in the database
     * @param userId   the unique ID of the user who was cancelled
     * @param callback called when the operation completes
     */
    public void notifyCancelled(String eventId, String userId, VoidCallback callback) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            userRemote.getUser(userId, user -> {
                if (user != null) {
                    Notification notification = new Notification(
                            userId,
                            user.getEmail(),
                            organizerId,
                            eventId,
                            "Your invitation to " + (event != null ? event.getTitle() : "the event") + " has expired.",
                            Notification.TYPE_NOT_SELECTED
                    );
                    remote.saveNotification(notification);
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            });
        });
    }

    // ── Callback Interface ───────────────────────────────────────────────────

    /**
     * Shuts down the background executor. Call from the owning lifecycle component's
     * onDestroy() / onTerminate() to prevent thread leaks.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Callback interface for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}