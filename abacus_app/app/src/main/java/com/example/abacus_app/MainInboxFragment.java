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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.List;

/**
 * Inbox screen — displays notifications for the current user.
 * Delegates to NotificationRepository via NotificationAdapter.
 */
public class MainInboxFragment extends Fragment {

    private static final String TAG = "MainInboxFragment";

    private NotificationRepository notificationRepository;
    private NotificationAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_inbox_fragment, container, false);

        swipeRefresh = view.findViewById(R.id.inbox_swipe_refresh);

        RecyclerView recyclerView = view.findViewById(R.id.notificationRecycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NotificationAdapter();
        recyclerView.setAdapter(adapter);

        notificationRepository = new NotificationRepository();

        UserLocalDataSource localDataSource = new UserLocalDataSource(requireContext());
        currentUserId = localDataSource.getUUIDSync();

        Log.d(TAG, "Loading notifications for UID: " + currentUserId);
        loadNotifications();

        swipeRefresh.setOnRefreshListener(() -> {
            loadNotifications();
            swipeRefresh.setRefreshing(false);
        });

        return view;
    }

    private void loadNotifications() {
        if (currentUserId != null) {
            notificationRepository.listenForNotifications(currentUserId,
                    notifications -> {
                        if (notifications != null) {
                            Log.d(TAG, "Received " + notifications.size() + " notifications");
                            adapter.setNotifications(notifications);
                        }
                    });
        } else {
            Log.e(TAG, "currentUserId is null — cannot load notifications");
        }
    }
}