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
 * It coordinates between different data sources (Users, Events, Registrations) to
 * construct appropriate notification messages for various scenarios.
 *
 * Role: Repository in the Domain/Data Layer (MVVM).
 *
 * Outstanding Issues:
 * - Thread management: Using a single-thread executor for all batch operations might
 *   become a bottleneck if many organizers draw lotteries simultaneously.
 */
public class NotificationRepository {

    private final NotificationRemoteDataSource remote;
    private final UserRemoteDataSource userRemote;
    private final EventRemoteDataSource eventRemote;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Initializes the repository with its required remote data sources.
     */
    public NotificationRepository() {
        this.remote = new NotificationRemoteDataSource();
        this.userRemote = new UserRemoteDataSource(com.google.firebase.firestore.FirebaseFirestore.getInstance());
        this.eventRemote = new EventRemoteDataSource();
    }

    /**
     * Notify a list of users that they have been selected for an event.
     *
     * @param eventId The ID of the event.
     * @param userIds The list of user IDs to notify.
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
     *
     * @param eventId The ID of the event.
     * @param userIds The list of user IDs to notify.
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

    /**
     * Sets up a real-time listener for notifications filtered by a user's email.
     *
     * @param email    The email to filter by.
     * @param listener The listener callback for updates.
     */
    public void listenForNotificationsByEmail(String email, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotificationsByEmail(email, listener);
    }

    /**
     * Sends notifications to winners and losers when the lottery of an event is drawn.
     * Also notifies the organizer that the lottery draw is complete.
     *
     * @param eventId  The unique ID of the event.
     * @param callback Called when the operation completes.
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
     *
     * @param eventId  The unique ID of the event.
     * @param userId   The ID of the replacement user.
     * @param callback Called when the operation completes.
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
     *
     * @param eventId  The ID of the event.
     * @param userId   The ID of the user whose spot was cancelled.
     * @param callback Called when the operation completes.
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
     *
     * @param eventId the ID of the event context
     * @param userIds the list of user IDs to receive the message
     * @param message the custom message text
     * @param type    the notification type
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
     * Notifies an organizer when an invited user declines their invitation.
     *
     * @param eventId The ID of the event.
     * @param userKey The ID of the user who declined.
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
     *
     * @param eventId The ID of the event.
     * @param userId  The ID of the user who left.
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

    /**
     * Notifies an organizer when an accepted entrant cancels their participation.
     *
     * @param eventId The ID of the event.
     * @param userId  The ID of the entrant who cancelled.
     * @param callback called when the operation completes
     */
    public void notifyOrganizerEntrantCancelled(String eventId, String userId, VoidCallback callback) {
        eventRemote.getEventByIdAsync(eventId, event -> {
            if (event == null) {
                if (callback != null) callback.onComplete(null);
                return;
            }
            String organizerId = event.getOrganizerId();
            if (organizerId == null) {
                if (callback != null) callback.onComplete(null);
                return;
            }

            userRemote.getUser(userId, user -> {
                String name = (user != null) ? user.getName() : "An entrant";
                userRemote.getUser(organizerId, organizer -> {
                    if (organizer != null) {
                        Notification notification = new Notification(
                                organizerId,
                                organizer.getEmail(),
                                organizerId,
                                eventId,
                                name + " has cancelled their participation in \"" + event.getTitle() + "\".",
                                Notification.TYPE_ENTRANT_CANCELLED
                        );
                        notification.setReceivedInInbox(organizer.getNotificationsEnabled());
                        remote.saveNotification(notification);
                    }
                    if (callback != null) callback.onComplete(null);
                });
            });
        });
    }

    /**
     * Shuts down the background executor service.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Callback interface for void operations.
     */
    public interface VoidCallback {
        /**
         * Called when the operation is complete.
         * @param error An exception if the operation failed, null otherwise.
         */
        void onComplete(Exception error);
    }
}
