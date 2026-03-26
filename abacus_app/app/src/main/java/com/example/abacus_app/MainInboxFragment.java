package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * Inbox screen — displays notifications for the current user.
 * Uses email as a stable identifier to survive app reinstalls.
 */
public class MainInboxFragment extends Fragment {

    private static final String TAG = "MainInboxFragment";

    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        notificationRepository = new NotificationRepository();

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remote = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(local, remote);

        userRepository.getProfile(user -> {
            if (user != null && user.getEmail() != null && !user.getEmail().isEmpty()) {
                Log.d(TAG, "Resolved email: " + user.getEmail() + ". Loading notifications...");
                startListening(user.getEmail());
            } else {
                Log.e(TAG, "Failed to resolve user email for notifications.");
            }
        });

        return view;
    }

    private void startListening(String email) {
        notificationRepository.listenForNotificationsByEmail(email, notifications -> {
            if (notifications != null && isAdded()) {
                Log.d(TAG, "Received " + notifications.size() + " notifications for " + email);
                adapter.setNotifications(notifications);
            }
        });
    }
}
