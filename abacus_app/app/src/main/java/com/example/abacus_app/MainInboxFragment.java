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
 * MainInboxFragment.java
 *
 * This fragment provides the central hub for users to receive and manage notifications.
 * It aggregates notifications from two primary sources:
 * 1. Status-based: Derived from the user's 'registrations' for events (e.g., Selected/Waitlisted).
 * 2. Custom: Direct messages stored in the 'notifications' collection.
 *
 * It respects the user's notification preference:
 * - If notifications are ON, all historical and new messages are shown.
 * - If notifications are OFF, only messages received *before* the user opted out remain 
 *   visible. New messages are stored in Firestore for admin logs but are filtered 
 *   out of the user's personal inbox via the 'receivedInInbox' flag.
 *
 * For administrators, it includes a toggle switch to view a global audit log of all 
 * notifications sent within the system.
 *
 * Role: View (Fragment) in the MVVM pattern.
 */
public class MainInboxFragment extends Fragment {

    private static final String TAG = "MainInboxFragment";

    private NotificationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmptyInbox;
    private RecyclerView recyclerView;
    private MaterialButtonToggleGroup toggleGroupAdmin;
    private User currentUser;
    private String currentUserId;
    private FirebaseFirestore db;
    private String currentUserEmail;
    private ManageEventViewModel manageEventViewModel;

    private boolean showAllLogs = false;
    private ListenerRegistration registrationListener;
    private ListenerRegistration customNotificationListener;
    private ListenerRegistration userIdNotificationListener;
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
        UserRemoteDataSource userRemoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(local, userRemoteDataSource);

        userRepository.getProfile(user -> {
            if (user != null) {
                currentUser = user;
                currentUserId = user.getUid();
                currentUserEmail = user.getEmail();
            } else {
                // Guest user — use device UUID
                currentUserId = local.getUUIDSync();
            }

            Log.d(TAG, "Loading notifications for UID: " + currentUserId);
            loadUserAndStart();
        });

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

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setBottomNavVisible(true);
        }
    }

    /**
     * Loads the current user's profile to determine role-based UI (Admin toggle) 
     * and starts the real-time data listeners.
     */
    private void loadUserAndStart() {
        if (currentUserId != null) {
            UserLocalDataSource local = new UserLocalDataSource(requireContext());
            UserRemoteDataSource userRemote = new UserRemoteDataSource(db);
            UserRepository userRepo = new UserRepository(local, userRemote);

            userRepo.getProfile(user -> {
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

    /**
     * Configures the click listeners for the notification adapter, including
     * accept/decline actions and navigation to event details.
     */
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

        adapter.setOnItemClickListener(new NotificationAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String eventId) {
                Bundle args = new Bundle();
                args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);
                // show event details page with no nav-bar
                ((MainActivity) getActivity()).setBottomNavVisible(false);
                ((MainActivity) requireActivity())
                        .showFragment(
                                R.id.eventDetailsFragment,
                                false,
                                args
                        );
            }
        });
    }

    /**
     * Processes the acceptance of an invitation. Handles logic for co-organizers
     * and private event auto-enrollment.
     * 
     * @param n     The notification object.
     * @param docId The document ID of the notification in Firestore.
     */
    private void acceptInvite(Notification n, String docId) {
        if (n.getEventId() == null) return;
        String eventId = n.getEventId();
        String notificationType = n.getType();

        // Determine if this is a co-organizer invitation or private event enrollment
        boolean isCoOrganizerInvite = "CO_ORGANIZER_INVITE".equals(notificationType);

        db.collection("events").document(eventId).get().addOnSuccessListener(eventDoc -> {
            if (eventDoc.exists()) {
                boolean isPrivate = Boolean.TRUE.equals(eventDoc.getBoolean("isPrivate"));

                if (isCoOrganizerInvite) {
                    // Co-organizer invitation: add as co-organizer
                    manageEventViewModel.addCoOrganizer(eventId, currentUserId);
                } else if (isPrivate && currentUserId != null) {
                    // Private event enrollment: auto-enroll as participant (no lottery needed)
                    WaitlistEntry entry = new WaitlistEntry(
                            currentUserId,
                            eventId,
                            WaitlistEntry.STATUS_INVITED,  // Auto-invited for private events
                            0,  // no lottery number
                            System.currentTimeMillis()
                    );
                    // Add to waitlist directly (join without lottery for private)
                    db.collection("events")
                            .document(eventId)
                            .collection("waitlist")
                            .document(currentUserId)
                            .set(entry)
                            .addOnFailureListener(e -> Log.e("Inbox", "Failed to enroll in private event", e));
                }
            }

            // Remove notification from Firestore
            db.collection("notifications").document(docId).delete()
                    .addOnSuccessListener(aVoid -> {
                        myNotifications.remove("MSG_" + docId);
                        updateUI();
                        Toast.makeText(getContext(), "Invitation accepted", Toast.LENGTH_SHORT).show();
                    });
        });
    }

    /**
     * Processes the decline of an invitation by updating the notification status in Firestore.
     * 
     * @param docId The document ID of the notification in Firestore.
     */
    private void declineInvite(String docId) {
        db.collection("notifications").document(docId)
                .update("status", Notification.STATUS_DECLINED)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Invitation declined", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Starts real-time listeners for all notification sources.
     */
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

        if (currentUserId != null && !currentUserId.isEmpty()) {
            userIdNotificationListener = db.collection("notifications")
                    .whereEqualTo("userId", currentUserId)
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

    /**
     * Removes all active Firestore snapshot listeners.
     */
    private void stopListening() {
        if (registrationListener != null) registrationListener.remove();
        if (customNotificationListener != null) customNotificationListener.remove();
        if (userIdNotificationListener != null) userIdNotificationListener.remove();
        if (allLogsListener != null) allLogsListener.remove();
    }

    /**
     * Converts raw registration documents into displayable Notification objects.
     */
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
                        // Registration status changes are always "received" as they 
                        // represent the ground truth of the user's lottery outcome.
                        myNotifications.put("REG_" + eventId, n);
                        if (!showAllLogs) updateUI();
                    }
                }
            });
        }
    }

    /**
     * Processes custom message documents from the 'notifications' collection.
     */
    private void processCustomNotifications(List<DocumentSnapshot> docs) {
        for (DocumentSnapshot doc : docs) {
            Notification n = doc.toObject(Notification.class);
            if (n != null) {
                // Personal Inbox Filter: Only show if the message was sent while 
                // the user's 'notificationsEnabled' was true.
                if (n.isReceivedInInbox()) {
                    myNotifications.put("MSG_" + doc.getId(), n);
                } else {
                    myNotifications.remove("MSG_" + doc.getId());
                }
            }
        }
        if (!showAllLogs) updateUI();
    }

    /**
     * Processes all system notifications for the administrator's global audit log.
     */
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

    /**
     * Updates the RecyclerView UI based on the current toggle selection (Inbox vs Admin Log).
     */
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

    /**
     * Helper to create a human-readable notification message from a registration status.
     */
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
