package com.example.abacus_app;

import android.os.Bundle;
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

    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;
    private String currentUserId; // This should be retrieved from device ID / Auth

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_notif_list, container, false);

        RecyclerView recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        notificationRepository = new NotificationRepository();
        
        // US 01.07.01: Be identified by device. 
        // For now, using a placeholder or retrieving from MainActivity if available.
        // In a real scenario, this would be Settings.Secure.ANDROID_ID or similar.
        currentUserId = "test_device_id"; 

        startListening();

        return view;
    }

    private void startListening() {
        notificationRepository.listenForNotifications(currentUserId, new NotificationRemoteDataSource.OnNotificationsUpdatedListener() {
            @Override
            public void onUpdate(List<Notification> notifications) {
                if (notifications != null) {
                    adapter.setNotifications(notifications);
                }
            }
        });
    }
}
