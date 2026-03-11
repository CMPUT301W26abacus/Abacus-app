package com.example.abacus_app;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;
import java.util.ArrayList;
import java.util.List;

public class NotificationRemoteDataSource {

    private final FirebaseFirestore db;

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
     * Listens for real-time updates to a user's notifications.
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
