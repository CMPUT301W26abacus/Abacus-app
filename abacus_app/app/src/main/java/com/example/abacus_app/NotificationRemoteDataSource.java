package com.example.abacus_app;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

/**
 * NotificationRemoteDataSource.java
 *
 * This class serves as the direct interface with Google Firebase Firestore for all
 * notification-related data operations. It handles low-level tasks such as setting
 * up real-time snapshot listeners and performing batch writes.
 *
 * Role: Remote Data Source in the Data Layer (MVVM).
 *
 * Outstanding Issues:
 * - Security: Firestore rules must be carefully configured to ensure users can only 
 *   listen to notifications where 'userEmail' matches their own authenticated identity.
 */
public class NotificationRemoteDataSource {

    private final FirebaseFirestore db;
    private static final String TAG = "NotificationRDS";

    /**
     * Listener interface to receive real-time updates when the notification list changes in Firestore.
     */
    public interface OnNotificationsUpdatedListener {
        /**
         * Called when new data is retrieved from Firestore.
         * @param notifications The updated list of notification objects.
         */
        void onUpdate(List<Notification> notifications);
    }

    /**
     * Initializes the data source with a Firestore instance.
     */
    public NotificationRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Persists a single notification to the 'notifications' collection.
     * 
     * @param notification The notification object to save.
     */
    public void saveNotification(Notification notification) {
        db.collection("notifications").add(notification);
    }

    /**
     * Efficiently persists multiple notifications in a single atomic transaction.
     * 
     * @param notifications The list of notifications to save.
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
     * Sets up a real-time listener for notifications for a specific email address.
     * Results are ordered by timestamp in descending order (newest first).
     * 
     * @param email    The recipient email address.
     * @param listener The callback to trigger on data changes.
     */
    public void listenForNotificationsByEmail(String email, OnNotificationsUpdatedListener listener) {
        if (email == null || email.isEmpty()) {
            Log.e(TAG, "Cannot listen: email is null or empty");
            return;
        }

        Log.d(TAG, "Setting up listener for email: " + email);
        db.collection("notifications")
                .whereEqualTo("userEmail", email)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        return;
                    }
                    if (value == null) return;

                    Log.d(TAG, "Snapshot updated. Document count: " + value.size());
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) list.add(n);
                    }
                    listener.onUpdate(list);
                });
    }

    /**
     * Sets up a real-time listener for notifications for a specific user ID.
     * 
     * @param userId   The recipient user ID.
     * @param listener The callback to trigger on data changes.
     */
    public void listenForNotifications(String userId, OnNotificationsUpdatedListener listener) {
        Log.d(TAG, "Setting up listener for userId: " + userId);
        db.collection("notifications")
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Listen failed: " + error.getMessage());
                        return;
                    }
                    if (value == null) return;

                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : value.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) list.add(n);
                    }
                    listener.onUpdate(list);
                });
    }
}
