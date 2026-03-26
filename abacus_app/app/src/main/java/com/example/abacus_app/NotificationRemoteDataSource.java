package com.example.abacus_app;

import android.util.Log;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

public class NotificationRemoteDataSource {

    private final FirebaseFirestore db;
    private static final String TAG = "NotificationRDS";

    public interface OnNotificationsUpdatedListener {
        void onUpdate(List<Notification> notifications);
    }

    public NotificationRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Saves a single notification to Firestore.
     */
    public void saveNotification(Notification notification) {
        db.collection("notifications").add(notification);
    }

    /**
     * Saves multiple notifications efficiently using a WriteBatch.
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
     * Listens for real-time updates to a user's notifications by email.
     * Restored .orderBy() for server-side sorting.
     * Note: This requires a composite index on (userEmail ASC, timestamp DESC).
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
     * Listens for real-time updates to a user's notifications by userId.
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
