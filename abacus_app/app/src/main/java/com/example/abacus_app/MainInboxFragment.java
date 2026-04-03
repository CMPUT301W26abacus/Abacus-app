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

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldPath;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inbox screen — combines status-based notifications from registrations
 * and custom messages from the notifications collection.
 * For Admins, provides a toggle to view all notification logs in the system.
 */
public class MainInboxFragment extends Fragment {

    private static final String TAG = "MainInboxFragment";

    private NotificationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyInbox;
    private RecyclerView recyclerView;
    private MaterialButtonToggleGroup toggleGroupAdmin;
    private String currentUserId;
    private FirebaseFirestore db;
    private String currentUserEmail;
    private ManageEventViewModel manageEventViewModel;
    private User currentUser;

    private boolean showAllLogs = false;
    private ListenerRegistration registrationListener;
    private ListenerRegistration customNotificationListener;
    private ListenerRegistration allLogsListener;

    // We store notifications in a map keyed by a unique ID to prevent duplicates
    private final Map<String, Notification> myNotifications = new HashMap<>();
    private final Map<String, Notification> allLogs = new HashMap<>();
    private final Map<String, String> organizerEmailCache = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);

        swipeRefresh = view.findViewById(R.id.inbox_swipe_refresh);
        tvEmptyInbox = view.findViewById(R.id.tv_empty_inbox);
        toggleGroupAdmin = view.findViewById(R.id.toggle_group_admin);

        db = FirebaseFirestore.getInstance();
        manageEventViewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);

        recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        setupNotificationActions();

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        currentUserId = local.getUUIDSync();

        loadUserAndStart();

        swipeRefresh.setOnRefreshListener(() -> {
            loadUserAndStart();
            swipeRefresh.setRefreshing(false);
        });

        toggleGroupAdmin.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                showAllLogs = (checkedId == R.id.btn_all_logs);
                adapter.setReadOnly(showAllLogs);
                updateUI();
            }
        });

        return view;
    }

    private void loadUserAndStart() {
        UserRemoteDataSource userRemote = new UserRemoteDataSource(db);
        if (currentUserId != null) {
            userRemote.getUser(currentUserId, user -> {
                if (user != null) {
                    currentUser = user;
                    currentUserEmail = user.getEmail();
                    
                    if ("admin".equals(user.getRole())) {
                        toggleGroupAdmin.setVisibility(View.VISIBLE);
                    } else {
                        toggleGroupAdmin.setVisibility(View.GONE);
                    }
                    
                    stopListening();
                    startListening();
                }
            });
        }
    }

    private void setupNotificationActions() {
        adapter.setOnNotificationActionListener(new NotificationAdapter.OnNotificationActionListener() {
            @Override
            public void onAccept(Notification notification) {
                if (showAllLogs) return;

                for (Map.Entry<String, Notification> entry : myNotifications.entrySet()) {
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
                if (showAllLogs) return;

                for (Map.Entry<String, Notification> entry : myNotifications.entrySet()) {
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
        manageEventViewModel.addCoOrganizer(n.getEventId(), currentUserId);
        db.collection("notifications").document(docId)
                .update("status", Notification.STATUS_ACCEPTED)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Invitation accepted", Toast.LENGTH_SHORT).show();
                });
    }

    private void declineInvite(String docId) {
        db.collection("notifications").document(docId)
                .update("status", Notification.STATUS_DECLINED)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Invitation declined", Toast.LENGTH_SHORT).show();
                });
    }

    private void startListening() {
        registrationListener = db.collection("registrations")
                .whereEqualTo("userId", currentUserId)
                .addSnapshotListener((value, error) -> {
                    if (error != null || value == null) return;
                    processRegistrations(value.getDocuments());
                });

        if (currentUserEmail != null && !currentUserEmail.isEmpty()) {
            customNotificationListener = db.collection("notifications")
                    .whereEqualTo("userEmail", currentUserEmail)
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) return;
                        processCustomNotifications(value.getDocuments());
                    });
        }

        if (currentUser != null && "admin".equals(currentUser.getRole())) {
            allLogsListener = db.collection("notifications")
                    .addSnapshotListener((value, error) -> {
                        if (error != null || value == null) return;
                        processAllLogs(value.getDocuments());
                    });
        }
    }

    private void stopListening() {
        if (registrationListener != null) registrationListener.remove();
        if (customNotificationListener != null) customNotificationListener.remove();
        if (allLogsListener != null) allLogsListener.remove();
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
                        myNotifications.put("REG_" + eventId, n);
                        if (!showAllLogs) updateUI();
                    }
                }
            });
        }
    }

    private void processCustomNotifications(List<DocumentSnapshot> docs) {
        for (DocumentSnapshot doc : docs) {
            Notification n = doc.toObject(Notification.class);
            if (n != null) {
                myNotifications.put("MSG_" + doc.getId(), n);
            }
        }
        if (!showAllLogs) updateUI();
    }

    private void processAllLogs(List<DocumentSnapshot> docs) {
        allLogs.clear();
        Set<String> newOrganizerIds = new HashSet<>();

        for (DocumentSnapshot doc : docs) {
            Notification n = doc.toObject(Notification.class);
            if (n != null) {
                allLogs.put(doc.getId(), n);
                String orgId = n.getOrganizerId();
                if (orgId != null && !organizerEmailCache.containsKey(orgId)) {
                    newOrganizerIds.add(orgId);
                }
            }
        }

        if (!newOrganizerIds.isEmpty()) {
            // Firestore limit is 10 for whereIn, but for this project's scale we'll assume it's small or fetch in chunks
            // For robust handling, fetch one by one if the list is large
            for (String orgId : newOrganizerIds) {
                db.collection("users").document(orgId).get().addOnSuccessListener(userDoc -> {
                    if (userDoc.exists()) {
                        String email = userDoc.getString("email");
                        if (email != null) {
                            organizerEmailCache.put(orgId, email);
                            adapter.setOrganizerEmails(organizerEmailCache);
                        }
                    }
                });
            }
        }

        if (showAllLogs) updateUI();
    }

    private void updateUI() {
        List<Notification> list;
        if (showAllLogs) {
            list = new ArrayList<>(allLogs.values());
            tvEmptyInbox.setText("No notification logs found");
            adapter.setOrganizerEmails(organizerEmailCache);
        } else {
            list = new ArrayList<>(myNotifications.values());
            tvEmptyInbox.setText("No notifications yet");
        }

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
        Notification n = new Notification(currentUserId, currentUserEmail, organizerId, eventId, msg, status);
        n.setTimestamp(time);
        return n;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopListening();
    }
}
