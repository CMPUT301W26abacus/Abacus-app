package com.example.abacus_app;

import com.google.firebase.firestore.FirebaseFirestore;

public class NotificationRemoteDataSource {

    private final FirebaseFirestore db;

    public NotificationRemoteDataSource() {
        db = FirebaseFirestore.getInstance();
    }

    public void saveNotification(Notification notification) {
        db.collection("notifications").add(notification);
    }
}