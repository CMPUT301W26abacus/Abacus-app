package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
    private TextView tvEmptyInbox;
    private RecyclerView recyclerView;
    private String currentUserId;
    private FirebaseFirestore db;
    private String currentUserEmail;
    private ManageEventViewModel manageEventViewModel;
    private RegistrationRepository registrationRepository;

    // We store notifications in a map keyed by a unique ID to prevent duplicates
    private final Map<String, Notification> allNotifications = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);

        swipeRefresh = view.findViewById(R.id.inbox_swipe_refresh);
        tvEmptyInbox = view.findViewById(R.id.tv_empty_inbox);

        db = FirebaseFirestore.getInstance();
        manageEventViewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);
        registrationRepository = new RegistrationRepository();

        recyclerView = view.findViewById(R.id.notificationRecycler);
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
                String eventId = notification.getEventId();
                if (eventId == null) return;

                if (Notification.TYPE_CO_ORGANIZER_INVITE.equals(notification.getType())) {
                    // Handle co-organizer invitation
                    acceptCoOrganizerInvite(notification);
                } else {
                    // Handle regular event invitation (move from 'invited' to 'accepted')
                    acceptEventInvitation(eventId);
                }
            }

            @Override
            public void onDecline(Notification notification) {
                String eventId = notification.getEventId();
                if (eventId == null) return;

                if (Notification.TYPE_CO_ORGANIZER_INVITE.equals(notification.getType())) {
                    declineCoOrganizerInvite(notification);
                } else {
                    declineEventInvitation(eventId);
                }
            }
        });
    }

    private void acceptCoOrganizerInvite(Notification n) {
        // Find the doc ID from the map key (e.g., MSG_docId)
        String docId = null;
        for (Map.Entry<String, Notification> entry : allNotifications.entrySet()) {
            if (entry.getValue().equals(n)) {
                if (entry.getKey().startsWith("MSG_")) {
                    docId = entry.getKey().substring(4);
                    break;
                }
            }
        }

        if (docId == null) return;

        manageEventViewModel.addCoOrganizer(n.getEventId(), currentUserId);
        db.collection("notifications").document(docId)
                .update("status", Notification.STATUS_ACCEPTED)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Co-organizer invitation accepted", Toast.LENGTH_SHORT).show();
                });
    }

    private void declineCoOrganizerInvite(Notification n) {
        String docId = null;
        for (Map.Entry<String, Notification> entry : allNotifications.entrySet()) {
            if (entry.getValue().equals(n)) {
                if (entry.getKey().startsWith("MSG_")) {
                    docId = entry.getKey().substring(4);
                    break;
                }
            }
        }

        if (docId == null) return;

        db.collection("notifications").document(docId)
                .update("status", Notification.STATUS_DECLINED)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Co-organizer invitation declined", Toast.LENGTH_SHORT).show();
                });
    }

    private void acceptEventInvitation(String eventId) {
        registrationRepository.acceptInvitation(currentUserId, eventId, error -> {
            if (error == null) {
                Toast.makeText(getContext(), "Event invitation accepted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Failed to accept: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void declineEventInvitation(String eventId) {
        registrationRepository.declineInvitation(currentUserId, eventId, error -> {
            if (error == null) {
                Toast.makeText(getContext(), "Event invitation declined", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getContext(), "Failed to decline: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
                    String organizerId = eventDoc.getString("organizerId");
                    boolean drawn = Boolean.TRUE.equals(eventDoc.getBoolean("lotteryDrawn"));

                    Notification n = createFromStatus(eventId, eventTitle, status, drawn, timestamp, organizerId);
                    if (n != null) {
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
            if (list.isEmpty()) {
                tvEmptyInbox.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmptyInbox.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setNotifications(list);
            }
        }
    }

    private Notification createFromStatus(String eventId, String title, String status, boolean drawn, long time, String organizerId) {
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
        // userId, userEmail, organizerId, eventId, message, type
        Notification n = new Notification(currentUserId, currentUserEmail, organizerId, eventId, msg, status);
        n.setTimestamp(time);
        return n;
    }
}
