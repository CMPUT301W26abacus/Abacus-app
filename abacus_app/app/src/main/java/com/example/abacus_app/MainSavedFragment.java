package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainSavedFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyMessage;
    private MaterialButton btnShowSaved, btnShowCoOrganized;

    private List<Event> displayList = new ArrayList<>();
    private EventAdapter adapter;
    private String userUid;

    private enum ViewMode { SAVED, CO_ORGANIZED }
    private ViewMode currentMode = ViewMode.SAVED;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.main_saved_fragment, container, false);

        recyclerView = view.findViewById(R.id.rv_saved_events);
        layoutEmpty = view.findViewById(R.id.layout_empty_saved);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_saved_message);
        btnShowSaved = view.findViewById(R.id.btn_show_saved);
        btnShowCoOrganized = view.findViewById(R.id.btn_show_coorganized);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        userUid = local.getUUIDSync();

        btnShowSaved.setOnClickListener(v -> switchMode(ViewMode.SAVED));
        btnShowCoOrganized.setOnClickListener(v -> switchMode(ViewMode.CO_ORGANIZED));

        switchMode(ViewMode.CO_ORGANIZED); // Default to co-organized as saved is not implemented

        return view;
    }

    private void switchMode(ViewMode mode) {
        currentMode = mode;
        updateButtonStyles();
        
        if (mode == ViewMode.SAVED) {
            loadSavedEvents();
        } else {
            loadCoOrganizedEvents();
        }
    }

    private void updateButtonStyles() {
        int activeColor = ContextCompat.getColor(requireContext(), R.color.orange);
        int inactiveColor = ContextCompat.getColor(requireContext(), R.color.grey);

        if (currentMode == ViewMode.SAVED) {
            btnShowSaved.setStrokeColor(android.content.res.ColorStateList.valueOf(activeColor));
            btnShowSaved.setTextColor(activeColor);
            btnShowCoOrganized.setStrokeColor(android.content.res.ColorStateList.valueOf(inactiveColor));
            btnShowCoOrganized.setTextColor(inactiveColor);
        } else {
            btnShowCoOrganized.setStrokeColor(android.content.res.ColorStateList.valueOf(activeColor));
            btnShowCoOrganized.setTextColor(activeColor);
            btnShowSaved.setStrokeColor(android.content.res.ColorStateList.valueOf(inactiveColor));
            btnShowSaved.setTextColor(inactiveColor);
        }
    }

    private void loadSavedEvents() {
        displayList.clear();
        // Not implemented yet
        updateUI();
    }

    private void loadCoOrganizedEvents() {
        if (userUid == null) return;

        FirebaseFirestore.getInstance().collection("events")
                .whereArrayContains("coOrganizers", userUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    displayList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Event event = doc.toObject(Event.class);
                        if (event.getEventId() == null) event.setEventId(doc.getId());
                        displayList.add(event);
                    }
                    updateUI();
                });
    }

    private void updateUI() {
        if (displayList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvEmptyMessage.setText(currentMode == ViewMode.SAVED ? "No saved events" : "You are not co-organizing any events");
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);

            adapter = new EventAdapter(
                    displayList,
                    (title, autoJoin) -> {
                        // Open details normally
                        String eventId = "";
                        for (Event e : displayList) {
                            if (e.getTitle().equals(title)) {
                                eventId = e.getEventId();
                                break;
                            }
                        }
                        Bundle args = new Bundle();
                        args.putString(EventDetailsFragment.ARG_EVENT_TITLE, title);
                        args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);
                        ((MainActivity) getActivity()).showFragment(R.id.eventDetailsFragment, false, args);
                    },
                    null, // No delete
                    event -> {
                        // Manage button click
                        Bundle args = new Bundle();
                        args.putString("EVENT_ID", event.getEventId());
                        args.putString("EVENT_TITLE", event.getTitle());
                        ((MainActivity) getActivity()).showFragment(R.id.organizerManageFragment, false, args);
                    },
                    false, // Not admin
                    userUid,
                    false // Not guest
            );
            recyclerView.setAdapter(adapter);
        }
    }
}
