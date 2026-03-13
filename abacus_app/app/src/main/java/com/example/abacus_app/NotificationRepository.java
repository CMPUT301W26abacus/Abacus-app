package com.example.abacus_app;

import java.util.ArrayList;
import java.util.List;

/**
 * Repository class that serves as an abstraction layer between the UI and the data sources.
 * This class follows the Repository design pattern, providing a clean API for the rest of the application
 * to manage notifications without needing to know about the underlying persistence mechanism (Firestore).
 *
 * Role: Coordinates the creation and retrieval of notifications, particularly for event-related outcomes.
 * Design Pattern: Repository.
 *
 * Outstanding Issues:
 * - No local caching (offline support) implemented at the repository level; relies entirely on Firestore's internal caching.
 * - Error propagation to the UI is currently limited.
 */
public class NotificationRepository {

    private final NotificationRemoteDataSource remote;

    /**
     * Initializes the repository with a new remote data source instance.
     */
    public NotificationRepository() {
        remote = new NotificationRemoteDataSource();
    }

    /**
     * Sends notifications to all selected users (winners).
     * Implements US 01.04.01 - Receive notification when chosen (win).
     *
     * @param eventId The unique identifier of the event.
     * @param userIds The list of user IDs who were selected for the event.
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
     * Sends notifications to all users who were not selected (losers).
     * Implements US 01.04.02 - Receive notification when not chosen (lose).
     *
     * @param eventId The unique identifier of the event.
     * @param userIds The list of user IDs who were not selected for the event.
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
     * Starts listening for real-time notification updates for a specific user.
     *
     * @param userId   The unique identifier of the user.
     * @param listener The callback listener for updates.
     */
    public void listenForNotifications(String userId, NotificationRemoteDataSource.OnNotificationsUpdatedListener listener) {
        remote.listenForNotifications(userId, listener);
    }
}
