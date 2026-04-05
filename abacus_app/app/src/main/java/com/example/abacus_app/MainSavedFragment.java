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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainSavedFragment extends Fragment {

    private RecyclerView recyclerView;
    private LinearLayout layoutEmpty;
    private TextView tvEmptyMessage;
    private MaterialButton btnShowSaved, btnShowCoOrganized;
    private SwipeRefreshLayout swipeRefresh;

    private List<Event> displayList = new ArrayList<>();
    private EventAdapter adapter;
    private String userUid;       // Firebase UID — used for saved lookup
    private String deviceUuid;    // Device UUID — used for co-organizer lookup (existing behaviour)

    private enum ViewMode { SAVED, CO_ORGANIZED }
    private ViewMode currentMode = ViewMode.SAVED;

    public MainSavedFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.main_saved_fragment, container, false);

        // ===== INIT VIEWS =====
        recyclerView = view.findViewById(R.id.rv_saved_events);
        layoutEmpty = view.findViewById(R.id.layout_empty_saved);
        tvEmptyMessage = view.findViewById(R.id.tv_empty_saved_message);
        btnShowSaved = view.findViewById(R.id.btn_show_saved);
        btnShowCoOrganized = view.findViewById(R.id.btn_show_coorganized);
        swipeRefresh = view.findViewById(R.id.saved_swipe_refresh);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        // Firebase UID for saved events (matches what EventAdapter writes)
        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        userUid = firebaseUser != null ? firebaseUser.getUid() : null;

        // Device UUID kept for co-organizer lookup (existing behaviour unchanged)
        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        deviceUuid = local.getUUIDSync();

        // ===== BUTTON LISTENERS =====
        btnShowSaved.setOnClickListener(v -> switchMode(ViewMode.SAVED));
        btnShowCoOrganized.setOnClickListener(v -> switchMode(ViewMode.CO_ORGANIZED));

        // ===== SWIPE REFRESH =====
        swipeRefresh.setOnRefreshListener(() -> {
            if (currentMode == ViewMode.SAVED) {
                loadSavedEvents();
            } else {
                loadCoOrganizedEvents();
            }
            swipeRefresh.setRefreshing(false);
        });

        // Default view
        switchMode(ViewMode.SAVED);

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

    // ── Saved Events ──────────────────────────────────────────────────────────

    private void loadSavedEvents() {
        if (userUid == null) {
            displayList.clear();
            updateUI();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users").document(userUid)
                .collection("saved")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        eventIds.add(doc.getId());
                    }
                    if (eventIds.isEmpty()) {
                        displayList.clear();
                        updateUI();
                        return;
                    }
                    fetchEventsByIds(eventIds);
                })
                .addOnFailureListener(e -> {
                    displayList.clear();
                    updateUI();
                });
    }

    /**
     * Fetches full event documents for a list of eventIds, then calls updateUI().
     */
    private void fetchEventsByIds(List<String> eventIds) {
        List<Event> fetched = new ArrayList<>();
        final int[] remaining = {eventIds.size()};

        for (String eventId : eventIds) {
            FirebaseFirestore.getInstance()
                    .collection("events").document(eventId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            Event event = snapshot.toObject(Event.class);
                            if (event != null && !Boolean.TRUE.equals(event.getIsDeleted())) {
                                if (event.getEventId() == null || event.getEventId().isEmpty()) {
                                    event.setEventId(snapshot.getId());
                                }
                                fetched.add(event);
                            }
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            displayList.clear();
                            displayList.addAll(fetched);
                            updateUI();
                        }
                    })
                    .addOnFailureListener(e -> {
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            displayList.clear();
                            displayList.addAll(fetched);
                            updateUI();
                        }
                    });
        }
    }

    // ── Co-Organized Events ───────────────────────────────────────────────────

    private void loadCoOrganizedEvents() {
        // coOrganizers stores Firebase UID (set by addCoOrganizer in ManageEventViewModel)
        if (userUid == null) return;

        FirebaseFirestore.getInstance().collection("events")
                .whereArrayContains("coOrganizers", userUid)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    displayList.clear();
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Event event = doc.toObject(Event.class);
                        if (event != null && !Boolean.TRUE.equals(event.getIsDeleted())) {
                            if (event.getEventId() == null) event.setEventId(doc.getId());
                            displayList.add(event);
                        }
                    }
                    updateUI();
                });
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private void updateUI() {
        if (!isAdded()) return;

        if (displayList.isEmpty()) {
            recyclerView.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
            tvEmptyMessage.setText(
                    currentMode == ViewMode.SAVED
                            ? "No saved events"
                            : "You are not co-organizing any events"
            );
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);

            boolean canManageEvents = false;
            if (getActivity() instanceof MainActivity) {
                String role = ((MainActivity) getActivity()).getEffectiveRole();
                canManageEvents = "organizer".equals(role) || "admin".equals(role);
            }

            adapter = new EventAdapter(
                    displayList,
                    (title, autoJoin) -> {
                        String eventId = "";
                        for (Event e : displayList) {
                            if (e.getTitle() != null && e.getTitle().equals(title)) {
                                eventId = e.getEventId() != null ? e.getEventId() : "";
                                break;
                            }
                        }

                        Bundle args = new Bundle();
                        args.putString(EventDetailsFragment.ARG_EVENT_TITLE, title);
                        args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);

                        ((MainActivity) getActivity()).showFragment(R.id.eventDetailsFragment, false, args);
                    },
                    null,
                    event -> {
                        Bundle args = new Bundle();
                        args.putString("EVENT_ID", event.getEventId());
                        args.putString("EVENT_TITLE", event.getTitle());

                        ((MainActivity) getActivity()).showFragment(R.id.organizerManageFragment, false, args);
                    },
                    false,
                    canManageEvents,
                    userUid,
                    false
            );

            recyclerView.setAdapter(adapter);
        }
    }
}