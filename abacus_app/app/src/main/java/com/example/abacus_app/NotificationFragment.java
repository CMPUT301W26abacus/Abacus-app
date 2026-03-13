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

import java.util.List;

/**
 * UI Fragment class responsible for displaying a list of notifications to the user.
 * This fragment acts as the View in the MVVM/MVC architectural pattern.
 * It observes notification data from the {@link NotificationRepository} and updates the {@link NotificationAdapter}.
 *
 * Role: Provides the "Inbox" interface where entrants can view win/loss notifications for events.
 * Design Pattern: Observer (via the repository's listener) and View in a layered architecture.
 *
 * Outstanding Issues:
 * - Currently lacks a "mark as read" or "delete" functionality for notifications.
 * - Background color is hardcoded to white, which may conflict with future dark mode support.
 */
public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";
    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;
    private String currentUserId;

    /**
     * Inflates the fragment layout, initializes the RecyclerView, and sets up the repository.
     *
     * @param inflater           The LayoutInflater object that can be used to inflate any views in the fragment.
     * @param container          The parent view that the fragment's UI should be attached to.
     * @param savedInstanceState If non-null, this fragment is being re-constructed from a previous saved state.
     * @return The View for the fragment's UI.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Ensure we have a background color to avoid "black screen" issues if theme is dark
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);
        view.setBackgroundResource(android.R.color.white); 

        RecyclerView recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        notificationRepository = new NotificationRepository();
        
        // US 01.07.01: Be identified by device. 
        // Fetching the actual UUID of the device/user
        UserLocalDataSource localDataSource = new UserLocalDataSource(requireContext());
        currentUserId = localDataSource.getUUIDSync();
        
        Log.d(TAG, "Listening for notifications for UID: " + currentUserId);

        startListening();

        return view;
    }

    /**
     * Begins listening for real-time notification updates for the current user.
     * This method leverages the repository to establish a stream of data from the remote data source.
     */
    private void startListening() {
        if (currentUserId == null) {
            Log.e(TAG, "Cannot start listening: currentUserId is null");
            return;
        }

        notificationRepository.listenForNotifications(currentUserId, new NotificationRemoteDataSource.OnNotificationsUpdatedListener() {
            @Override
            public void onUpdate(List<Notification> notifications) {
                if (notifications != null) {
                    Log.d(TAG, "Received " + notifications.size() + " notifications");
                    adapter.setNotifications(notifications);
                }
            }
        });
    }
}
