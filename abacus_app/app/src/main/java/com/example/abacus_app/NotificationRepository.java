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

    /**
     * Sends a custom manual notification to a list of users.
     */
    public void sendManualNotification(String eventId, List<String> userIds, String message, String type) {
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
                                message,
                                type
                        );
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

    public void notifySelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            for (String userId : userIds) {
                userRemote.getUser(userId, user -> {
                    if (user != null) {
                        Notification notification = new Notification(userId, user.getEmail(), organizerId, eventId, "Congratulations! You have been selected for the event.", Notification.TYPE_SELECTED);
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

    public void notifyNotSelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            for (String userId : userIds) {
                userRemote.getUser(userId, user -> {
                    if (user != null) {
                        Notification notification = new Notification(userId, user.getEmail(), organizerId, eventId, "We regret to inform you that you were not selected for the event this time.", Notification.TYPE_NOT_SELECTED);
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

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
                                notification = new Notification(userId, user.getEmail(), organizerId, eventId, "Congratulations! You have been invited to " + (event != null ? event.getTitle() : "the event"), Notification.TYPE_SELECTED);
                            } else {
                                notification = new Notification(userId, user.getEmail(), organizerId, eventId, "The lottery for " + (event != null ? event.getTitle() : "the event") + " has been drawn. Unfortunately you have not been selected at this time.", Notification.TYPE_NOT_SELECTED);
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

    public void notifyReplacement(String eventId, String userId, VoidCallback callback) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            String organizerId = (event != null) ? event.getOrganizerId() : null;
            userRemote.getUser(userId, user -> {
                if (user != null) {
                    Notification notification = new Notification(userId, user.getEmail(), organizerId, eventId, "Congratulations! You have been selected as a replacement for " + (event != null ? event.getTitle() : "the event") + ".", Notification.TYPE_SELECTED);
                    remote.saveNotification(notification);
                }
                if (callback != null) mainHandler.post(() -> callback.onComplete(null));
            });
        });
    }

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
                            "Your entry for " + (event != null ? event.getTitle() : "the event") + " has been cancelled.",
                            Notification.TYPE_CANCELED
                    );
                    remote.saveNotification(notification);
                }
                if (callback != null) mainHandler.post(() -> callback.onComplete(null));
            });
        });
    }

    public void listenForNotificationsByEmail(String email, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotificationsByEmail(email, listener);
    }

    public void shutdown() {
        executor.shutdown();
    }

    public interface VoidCallback {
        void onComplete(Exception error);
    }
}