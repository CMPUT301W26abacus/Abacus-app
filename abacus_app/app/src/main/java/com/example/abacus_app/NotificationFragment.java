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
 * Fragment to display a list of notifications for the entrant.
 * Implements US 01.04.01 and US 01.04.02 by showing win/lose notifications.
 */
public class NotificationFragment extends Fragment {

    private static final String TAG = "NotificationFragment";
    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Ensure we have a background color to avoid "black screen" issues if theme is dark
        View view = inflater.inflate(R.layout.fragment_notif_list, container, false);
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
