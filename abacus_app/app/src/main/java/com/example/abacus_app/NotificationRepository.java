package com.example.abacus_app;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import android.os.Handler;
import android.os.Looper;

import com.google.firebase.firestore.FirebaseFirestore;

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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationRepository() {
        this.remote = new NotificationRemoteDataSource();
        this.userRemote = new UserRemoteDataSource(com.google.firebase.firestore.FirebaseFirestore.getInstance());
    }

    // ── Selected / Not Selected Notifications ────────────────────────────────

    /**
     * Notify users they have been selected for an event.
     */
    public void notifySelected(String eventId, List<String> userIds, VoidCallback callback) {
        executor.submit(() -> {
            try {
                if (userIds == null || userIds.isEmpty()) return;

                for (String userId : userIds) {
                    User user = userRemote.getUserSync(userId);
                    if (user != null) {
                        Notification notification = new Notification(
                                userId,
                                user.getEmail(), // <-- now included
                                eventId,
                                "Congratulations! You have been selected for the event.",
                                Notification.TYPE_SELECTED
                        );
                        remote.saveNotification(notification);
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Notify users they were not selected for an event.
     */
    public void notifyNotSelected(String eventId, List<String> userIds, VoidCallback callback) {
        executor.submit(() -> {
            try {
                if (userIds == null || userIds.isEmpty()) return;

                for (String userId : userIds) {
                    User user = userRemote.getUserSync(userId);
                    if (user != null) {
                        Notification notification = new Notification(
                                userId,
                                user.getEmail(), // <-- included
                                eventId,
                                "We regret to inform you that you were not selected for the event this time.",
                                Notification.TYPE_NOT_SELECTED
                        );
                        remote.saveNotification(notification);
                    }
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
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
     * @param eventId the unique ID of the event in the database
     * @param callback called when the operation completes
     */
    public void notifyLotteryResults(String eventId, VoidCallback callback) {
        executor.submit(() -> {
           try {
               RegistrationRemoteDataSource registrationRDS = new RegistrationRemoteDataSource();
               EventRemoteDataSource eventRDS = new EventRemoteDataSource();

               ArrayList<WaitlistEntry> entries = registrationRDS.getEntriesSync(eventId);
               Event event = eventRDS.getEventById(eventId);

               ArrayList<Notification> notifications = new ArrayList<>();
               for (WaitlistEntry entry : entries) {
                   // get user email
                   User entryUser = userRemote.getUserSync(entry.getUserID());

                   // send appropriate notification
                   if (entry.getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
                       notifications.add(new Notification(
                               entry.getUserId(),
                               eventId,
                               entryUser.getEmail(),
                               "Congratulations! You have been invited to " + event.getTitle(),
                               Notification.TYPE_SELECTED
                       ));
                   } else if (entry.getStatus().equals(WaitlistEntry.STATUS_WAITLISTED)) {
                       notifications.add(new Notification(
                               entry.getUserId(),
                               eventId,
                               entryUser.getEmail(),
                               "The lottery for " + event.getTitle() + " has been drawn. Unfortunately you have not been selected at this time.",
                               Notification.TYPE_NOT_SELECTED
                       ));
                   }
               }

               remote.saveNotificationsBatch(notifications);
               mainHandler.post(() -> callback.onComplete(null));
           } catch (Exception e) {
               mainHandler.post(() -> callback.onComplete(e));
           }
        });
    }

    /**
     * Notifies a single user that they have been invited to an event.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId the unique ID of the user who was drawn
     * @param callback called when the operation completes
     */
    public void notifyReplacement(String eventId, String userId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                EventRemoteDataSource eventRDS = new EventRemoteDataSource();
                Event event = eventRDS.getEventById(eventId);
                User entryUser = userRemote.getUserSync(userId);

                Notification notification = (new Notification(
                        userId,
                        eventId,
                        entryUser.getEmail(),
                        "The lottery for " + event.getTitle() + " has been drawn. Unfortunately you have not been selected at this time.",
                        Notification.TYPE_SELECTED
                ));

                remote.saveNotification(notification);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Notifies a single user that they have been cancelled from an event.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId the unique ID of the user who was cancelled
     * @param callback called when the operation completes
     */
    public void notifyCancelled(String eventId, String userId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                EventRemoteDataSource eventRDS = new EventRemoteDataSource();
                Event event = eventRDS.getEventById(eventId);
                User entryUser = userRemote.getUserSync(userId);

                Notification notification = (new Notification(
                        userId,
                        eventId,
                        entryUser.getEmail(),
                        "Your invitation to " + event.getTitle() + " has expired.",
                        Notification.TYPE_CANCELED
                ));

                remote.saveNotification(notification);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
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