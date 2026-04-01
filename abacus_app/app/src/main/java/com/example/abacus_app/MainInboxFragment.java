package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inbox screen — combines status-based notifications from registrations
 * and custom messages from the notifications collection.
 */
public class MainInboxFragment extends Fragment {

    private static final String TAG = "MainInboxFragment";

    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private String currentUserId;
    private FirebaseFirestore db;
    private String currentUserEmail;
    private ManageEventViewModel manageEventViewModel;

    // We store notifications in a map keyed by a unique ID to prevent duplicates
    private final Map<String, Notification> allNotifications = new HashMap<>();
    private final Map<String, String> notificationDocIds = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);

        swipeRefresh = view.findViewById(R.id.inbox_swipe_refresh);

        db = FirebaseFirestore.getInstance();
        manageEventViewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);

        RecyclerView recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        setupNotificationActions();

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        currentUserId = local.getUUIDSync();

        Log.d(TAG, "Loading notifications for UID: " + currentUserId);
        loadNotifications();

        swipeRefresh.setOnRefreshListener(() -> {
            loadNotifications();
            swipeRefresh.setRefreshing(false);
        });

        return view;
    }

    private void loadNotifications() {
        UserRemoteDataSource userRemote = new UserRemoteDataSource(db);
        if (currentUserId != null) {
            userRemote.getUser(currentUserId, user -> {
                if (user != null) {
                    currentUserEmail = user.getEmail();
                    startListening();
                }
            });
        }

    }

    private void setupNotificationActions() {
        adapter.setOnNotificationActionListener(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onAccept(Notification notification) {
                String docId = notificationDocIds.get("MSG_" + notification.getTimestamp()); // Use timestamp as proxy or store docId
                // Better: we need the actual doc ID from Firestore to delete/update it.
                // Let's find the correct MSG_ key.
                for (Map.Entry<String, Notification> entry : allNotifications.entrySet()) {
                    if (entry.getValue().equals(notification)) {
                        String key = entry.getKey();
                        if (key.startsWith("MSG_")) {
                            String actualDocId = key.substring(4);
                            acceptInvite(notification, actualDocId);
                            break;
                        }
                    }
                }
            }

            @Override
            public void onDecline(Notification notification) {
                for (Map.Entry<String, Notification> entry : allNotifications.entrySet()) {
                    if (entry.getValue().equals(notification)) {
                        String key = entry.getKey();
                        if (key.startsWith("MSG_")) {
                            String actualDocId = key.substring(4);
                            declineInvite(actualDocId);
                            break;
                        }
                    }
                }
            }
        });
    }

    private void acceptInvite(Notification n, String docId) {
        if (n.getEventId() == null) return;

        // Use the ViewModel method we created earlier to handle the logic
        manageEventViewModel.addCoOrganizer(n.getEventId(), currentUserId);

        // Remove notification from Firestore
        db.collection("notifications").document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    allNotifications.remove("MSG_" + docId);
                    updateUI();
                    Toast.makeText(getContext(), "Invitation accepted", Toast.LENGTH_SHORT).show();
                });
    }

    private void declineInvite(String docId) {
        db.collection("notifications").document(docId).delete()
                .addOnSuccessListener(aVoid -> {
                    allNotifications.remove("MSG_" + docId);
                    updateUI();
                    Toast.makeText(getContext(), "Invitation declined", Toast.LENGTH_SHORT).show();
                });
    }

    private void startListening() {
        // 1. Listen to Registration Status Changes (Lottery Results)
        db.collection("registrations")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    processRegistrations(value.getDocuments());
                });

        // 2. Listen to Custom Messages (Organizer Announcements + Invites)
        if (currentUserEmail != null && !currentUserEmail.isEmpty()) {
            db.collection("notifications")
                    .whereEqualTo("userEmail", currentUserEmail)
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) return;
                        processCustomNotifications(value.getDocuments());
                    });
        }
    }

    private void processRegistrations(List<DocumentSnapshot> docs) {
        for (DocumentSnapshot doc : docs) {
            String eventId = doc.getString("eventId");
            String status = doc.getString("status");
            long timestamp = doc.getLong("timestamp") != null ? doc.getLong("timestamp") : 0;

            db.collection("events").document(eventId).get().addOnSuccessListener(eventDoc -> {
                if (eventDoc.exists()) {
                    String eventTitle = eventDoc.getString("title");
                    boolean drawn = Boolean.TRUE.equals(eventDoc.getBoolean("lotteryDrawn"));

                    Notification n = createFromStatus(eventId, eventTitle, status, drawn, timestamp);
                    if (n != null) {
                        // Key by eventId + status to avoid duplicate status updates
                        allNotifications.put("REG_" + eventId, n);
                        updateUI();
                    }
                }
            });
        }
    }

    private void processCustomNotifications(List<DocumentSnapshot> docs) {
        for (DocumentSnapshot doc : docs) {
            Notification n = doc.toObject(Notification.class);
            if (n != null) {
                allNotifications.put("MSG_" + doc.getId(), n);
            }
        }
        updateUI();
    }

    private void updateUI() {
        List<Notification> list = new ArrayList<>(allNotifications.values());
        // Sort newest first
        Collections.sort(list, (n1, n2) -> Long.compare(n2.getTimestamp(), n1.getTimestamp()));
        if (isAdded()) {
            adapter.setNotifications(list);
        }
    }

    private Notification createFromStatus(String eventId, String title, String status, boolean drawn, long time) {
        String msg = null;
        if ("invited".equals(status) || "accepted".equals(status)) {
            msg = "Congratulations! You were selected for " + title + ". Please accept or decline.";
        } else if ("waitlisted".equals(status) && drawn) {
            msg = "We regret to inform you that you were not selected for " + title + ".";
        } else if ("declined".equals(status)) {
            msg = "You have declined the invitation for " + title + ".";
        } else if ("cancelled".equals(status)) {
            msg = "Your spot for " + title + " was cancelled.";
        }

        if (msg == null) return null;
        Notification n = new Notification(currentUserId, "", eventId, msg, status);
        n.setTimestamp(time);
        return n;
    }
}
