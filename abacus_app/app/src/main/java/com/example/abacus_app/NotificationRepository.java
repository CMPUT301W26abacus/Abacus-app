package com.example.abacus_app;

import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final NotificationRemoteDataSource remote;

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
}
