package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationRepository {

    private final NotificationRemoteDataSource remote;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new Handler(Looper.getMainLooper());

    public NotificationRepository() {
        remote = new NotificationRemoteDataSource();
    }

    /**
     * US 01.04.01 - Receive notification when chosen (win)
     * Sends notifications to all selected users.
     */
    public void notifySelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        List<Notification> notifications = new ArrayList<>();
        for (String userId : userIds) {
            notifications.add(new Notification(
                    userId,
                    eventId,
                    "Congratulations! You have been selected for the event.",
                    "SELECTED"
            ));
        }
        remote.saveNotificationsBatch(notifications);
    }

    /**
     * US 01.04.02 - Receive notification when not chosen (lose)
     * Sends notifications to all users who were not selected.
     */
    public void notifyNotSelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        List<Notification> notifications = new ArrayList<>();
        for (String userId : userIds) {
            notifications.add(new Notification(
                    userId,
                    eventId,
                    "We regret to inform you that you were not selected for the event this time.",
                    "NOT_SELECTED"
            ));
        }
        remote.saveNotificationsBatch(notifications);
    }

    /**
     * Starts listening for notifications for a specific user.
     */
    public void listenForNotifications(String userId, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotifications(userId, listener);
    }

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
                   if (entry.getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
                       notifications.add(new Notification(
                               entry.getUserId(),
                               eventId,
                               "Congratulations! You have been invited to " + event.getTitle(),
                               Notification.TYPE_SELECTED
                       ));
                   } else if (entry.getStatus().equals(WaitlistEntry.STATUS_WAITLISTED)) {
                       notifications.add(new Notification(
                               entry.getUserId(),
                               eventId,
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
     * Notifies a single user that they have been draw for the lottery of an event.
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

                Notification notification = (new Notification(
                        userId,
                        eventId,
                        "Congratulations! You have been invited to " + event.getTitle(),
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
     * @param userId the unique ID of the user who was drawn
     * @param callback called when the operation completes
     */
    public void notifyCancelled(String eventId, String userId, VoidCallback callback) {
        executor.submit(() -> {
            try {
                EventRemoteDataSource eventRDS = new EventRemoteDataSource();
                Event event = eventRDS.getEventById(eventId);

                Notification notification = (new Notification(
                        userId,
                        eventId,
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

    /**
     * Callback interface for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
