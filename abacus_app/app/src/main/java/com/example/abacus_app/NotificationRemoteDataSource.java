package com.example.abacus_app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

/**
 * Data source class that interacts directly with the Google Firebase Firestore database.
 * This class follows the Data Source design pattern, encapsulating the persistence logic
 * for notifications.
 *
 * Role: Provides the low-level API for CRUD operations and real-time listeners on the
 * "notifications" collection in Firestore.
 *
 * Design Pattern: Data Access Object (DAO) / Data Source.
 *
 * Outstanding Issues:
 * - Error handling in Firestore listeners is minimal (currently just returns on error).
 * - No pagination implemented; fetching a very large number of notifications may affect performance.
 */
public class NotificationRemoteDataSource {

    private final FirebaseFirestore db;

    /**
     * Interface for receiving updates when notifications are retrieved or changed in Firestore.
     */
    public interface OnNotificationsUpdatedListener {
        /**
         * Called when the list of notifications for a user has been updated.
         *
         * @param notifications The updated list of {@link Notification} objects.
         */
        void onUpdate(List<Notification> notifications);
    }

    /**
     * Initializes the Firestore instance.
     */
    public NotificationRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Saves a single notification to the Firestore "notifications" collection.
     *
     * @param notification The {@link Notification} object to persist.
     */
    public void saveNotification(Notification notification) {
        db.collection("notifications").add(notification);
    }

    /**
     * Saves multiple notifications efficiently using a Firestore {@link WriteBatch}.
     * This is an atomic operation; all saves will succeed or none will.
     *
     * @param notifications The list of {@link Notification} objects to persist.
     */
    public void saveNotificationsBatch(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) return;

        WriteBatch batch = db.batch();
        for (Notification notification : notifications) {
            batch.set(db.collection("notifications").document(), notification);
        }
        batch.commit();
    }

    /**
     * Establishes a real-time listener for notifications targeted at a specific user.
     * Notifications are ordered by timestamp in descending order (newest first).
     *
     * @param userId   The unique identifier of the user whose notifications to watch.
     * @param listener The callback to trigger when data changes.
     */
    public void listenForNotifications(String userId, OnNotificationsUpdatedListener listener) {
        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        list.add(doc.toObject(Notification.class));
                    }
                    listener.onUpdate(list);
                });
    }
}
