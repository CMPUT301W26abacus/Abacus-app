package com.example.abacus_app;

import java.util.List;

public class NotificationRepository {

    private final NotificationRemoteDataSource remote;

    public NotificationRepository() {
        remote = new NotificationRemoteDataSource();
    }

    // US 01.04.01
    public void notifySelected(String eventId, List<String> userIds) {

        for (String userId : userIds) {

            Notification notification = new Notification(
                    userId,
                    eventId,
                    "You were selected for this event.",
                    "SELECTED"
            );

            remote.saveNotification(notification);
        }
    }

    // US 01.04.02
    public void notifyNotSelected(String eventId, List<String> userIds) {

        for (String userId : userIds) {

            Notification notification = new Notification(
                    userId,
                    eventId,
                    "You were not selected for this event.",
                    "NOT_SELECTED"
            );

            remote.saveNotification(notification);
        }
    }
}