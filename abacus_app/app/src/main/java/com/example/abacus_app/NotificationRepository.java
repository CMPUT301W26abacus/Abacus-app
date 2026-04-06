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
 * This repository handles the business logic for creating and sending notifications.
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
     * Notify a list of users that they have been selected for an event.
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
                        notification.setReceivedInInbox(user.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

    /**
     * Notify a list of users they were not selected for an event.
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
                        notification.setReceivedInInbox(user.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

    public void listenForNotificationsByEmail(String email, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotificationsByEmail(email, listener);
    }

    /**
     * Sends notifications to winners and losers when the lottery of an event is drawn.
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
                            } else {
                                notification = new Notification(
                                        userId,
                                        user.getEmail(),
                                        organizerId,
                                        eventId,
                                        "The lottery for " + (event != null ? event.getTitle() : "the event") + " has been drawn. Unfortunately you have not been selected at this time.",
                                        Notification.TYPE_NOT_SELECTED
                                );
                            }
                            notification.setReceivedInInbox(user.getNotificationsEnabled());
                            remote.saveNotification(notification);
                        }
                    });
                }

                // Notify Organizer
                if (organizerId != null) {
                    userRemote.getUser(organizerId, organizer -> {
                        if (organizer != null) {
                            Notification orgNotif = new Notification(
                                    organizerId,
                                    organizer.getEmail(),
                                    organizerId,
                                    eventId,
                                    "Lottery draw for \"" + (event != null ? event.getTitle() : "your event") + "\" has been successfully completed.",
                                    Notification.TYPE_MANUAL
                            );
                            orgNotif.setReceivedInInbox(organizer.getNotificationsEnabled());
                            remote.saveNotification(orgNotif);
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
     * Notifies a single user that they have been drawn as a replacement.
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
                            "Congratulations! You have been selected as a replacement for " + (event != null ? event.getTitle() : "the event") + ".",
                            Notification.TYPE_SELECTED
                    );
                    notification.setReceivedInInbox(user.getNotificationsEnabled());
                    remote.saveNotification(notification);
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            });
        });
    }

    /**
     * Notifies a single user that their invitation has expired or been cancelled.
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
                            Notification.TYPE_CANCELED
                    );
                    notification.setReceivedInInbox(user.getNotificationsEnabled());
                    remote.saveNotification(notification);
                }
                if (callback != null) {
                    mainHandler.post(() -> callback.onComplete(null));
                }
            });
        });
    }

    /**
     * Sends custom manual notifications to a specific list of users.
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
                        notification.setReceivedInInbox(user.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                });
            }
        });
    }

    /**
     * Notifies an organizer when an invited user declines.
     */
    public void notifyOrganizerDecline(String eventId, String userKey) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            if (event == null) return;
            String organizerId = event.getOrganizerId();
            if (organizerId == null) return;

            userRemote.getUser(userKey, userWhoDeclined -> {
                String name = (userWhoDeclined != null) ? userWhoDeclined.getName() : "A user";
                userRemote.getUser(organizerId, organizer -> {
                    if (organizer != null) {
                        Notification notification = new Notification(
                                organizerId,
                                organizer.getEmail(),
                                organizerId,
                                eventId,
                                name + " has declined the invitation for " + event.getTitle() + ".",
                                Notification.TYPE_MANUAL
                        );
                        notification.setReceivedInInbox(organizer.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                });
            });
        });
    }

    /**
     * Notifies an organizer when a user leaves the waitlist voluntarily.
     */
    public void notifyOrganizerLeftWaitlist(String eventId, String userId) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            if (event == null) return;
            String organizerId = event.getOrganizerId();
            if (organizerId == null) return;

            userRemote.getUser(userId, user -> {
                String name = (user != null) ? user.getName() : "A user";
                userRemote.getUser(organizerId, organizer -> {
                    if (organizer != null) {
                        Notification notification = new Notification(
                                organizerId,
                                organizer.getEmail(),
                                organizerId,
                                eventId,
                                name + " has left the waiting list for \"" + event.getTitle() + "\".",
                                Notification.TYPE_MANUAL
                        );
                        notification.setReceivedInInbox(organizer.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                });
            });
        });
    }

    public void shutdown() {
        executor.shutdown();
    }

    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
