package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * UI Controller for the active event management screen.
 * Shows the organizer's events, then the waitlist for a selected event.
 * Owner: Himesh
 */
public class OrganizerManageFragment extends Fragment {

    private ManageEventViewModel viewModel;
    private RecyclerView recyclerView;
    private WaitlistAdapter waitlistAdapter;
    private TextView tvEventName, tvCount;

    private List<WaitlistEntry> entries = new ArrayList<>();

    // Two modes: EVENT_LIST shows the organizer's events; WAITLIST shows one event's waitlist
    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;
    private String selectedEventId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organizer_manage_fragment, container, false);
        viewModel    = new ViewModelProvider(this).get(ManageEventViewModel.class);
        tvEventName  = view.findViewById(R.id.tv_event_name);
        tvCount      = view.findViewById(R.id.tv_waitlist_count);
        recyclerView = view.findViewById(R.id.rv_waitlist);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST) {
                showEventList();
            } else {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
                }
            }
        });

        observeViewModel();
        showEventList();
        return view;
    }

    private void showEventList() {
        currentMode = Mode.EVENT_LIST;
        tvEventName.setText("My Events");
        tvCount.setText("");

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        String uuid = local.getUUIDSync();
        if (uuid != null) {
            viewModel.loadOrganizerEvents(uuid);
        } else {
            tvCount.setText("Could not load events");
        }
    }

    private void observeViewModel() {
        // Event list mode
        viewModel.getEvents().observe(getViewLifecycleOwner(), eventList -> {
            if (currentMode != Mode.EVENT_LIST) return;
            if (eventList == null || eventList.isEmpty()) {
                tvCount.setText("No events found");
                recyclerView.setAdapter(null);
                return;
            }
            tvCount.setText(eventList.size() + " event(s)");

            recyclerView.setAdapter(new EventAdapter(eventList, title -> {
                for (Event e : eventList) {
                    if (title.equals(e.getTitle())) {
                        selectedEventId = e.getEventId();
                        showWaitlist(e.getTitle(), selectedEventId);
                        break;
                    }
                }
            }));
        });

        // Waitlist mode
        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (currentMode != Mode.WAITLIST) return;
            if (newEntries != null) {
                entries.clear();
                entries.addAll(newEntries);
                tvCount.setText("Total Entrants: " + entries.size());
                waitlistAdapter.notifyDataSetChanged();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });
    }

    private void showWaitlist(String eventTitle, String eventId) {
        currentMode = Mode.WAITLIST;
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");

        waitlistAdapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
    }

    /** Still supported if navigated to with args from elsewhere */
    public static OrganizerManageFragment newInstance(String eventId, String eventTitle) {
        OrganizerManageFragment fragment = new OrganizerManageFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID", eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }
}