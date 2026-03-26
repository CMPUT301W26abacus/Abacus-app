package com.example.abacus_app;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;

public class NotificationRepository {

    private final NotificationRemoteDataSource remote;
    private final UserRemoteDataSource userRemote;

    public NotificationRepository() {
        this.remote = new NotificationRemoteDataSource();
        this.userRemote = new UserRemoteDataSource(com.google.firebase.firestore.FirebaseFirestore.getInstance());
    }

    /**
     * US 01.04.01 - Receive notification when chosen (win)
     * Fetches emails for each user and sends notifications.
     */
    public void notifySelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        for (String userId : userIds) {
            userRemote.getUser(userId, user -> {
                if (user != null) {
                    Notification notification = new Notification(
                            userId,
                            user.getEmail(),
                            eventId,
                            "Congratulations! You have been selected for the event.",
                            "SELECTED"
                    );
                    remote.saveNotification(notification);
                }
            });
        }
    }

    /**
     * US 01.04.02 - Receive notification when not chosen (lose)
     * Fetches emails for each user and sends notifications.
     */
    public void notifyNotSelected(String eventId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) return;

        for (String userId : userIds) {
            userRemote.getUser(userId, user -> {
                if (user != null) {
                    Notification notification = new Notification(
                            userId,
                            user.getEmail(),
                            eventId,
                            "We regret to inform you that you were not selected for the event this time.",
                            "NOT_SELECTED"
                    );
                    remote.saveNotification(notification);
                }
            });
        }
    }

    /**
     * Listens for notifications filtered by user email for stable identity.
     */
    public void listenForNotificationsByEmail(String email, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotificationsByEmail(email, listener);
    }
}
