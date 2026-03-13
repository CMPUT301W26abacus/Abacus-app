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
 * Provides a two-mode UI:
 * 1. EVENT_LIST: Displays all events created by the organizer.
 * 2. WAITLIST: Displays the list of entrants for a selected event.
 * 
 * Implements:
 * - US 02.02.01: View the list of entrants who joined the event waiting list.
 * - US 02.06.01 - 02.06.05: Management of chosen, cancelled, and enrolled entrants.
 * 
 * @author Himesh
 * @version 1.1
 */
public class OrganizerManageFragment extends Fragment {

    private ManageEventViewModel viewModel;
    private RecyclerView recyclerView;
    private WaitlistAdapter waitlistAdapter;
    private TextView tvEventName, tvCount;

    private List<WaitlistEntry> entries = new ArrayList<>();

    /** Modes to distinguish between viewing all events or a specific waitlist */
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

    /**
     * Switches the UI to show the list of events owned by the current organizer.
     */
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

    /**
     * Sets up observers for the ViewModel data.
     */
    private void observeViewModel() {
        // Observer for the list of events (EVENT_LIST mode)
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

        // Observer for the waitlist entries (WAITLIST mode)
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

    /**
     * Switches the UI to show the waitlist for a specific event.
     * US 02.02.01.
     *
     * @param eventTitle The title of the event to display.
     * @param eventId    The ID of the event to load entrants for.
     */
    private void showWaitlist(String eventTitle, String eventId) {
        currentMode = Mode.WAITLIST;
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");

        waitlistAdapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
    }

    /**
     * Factory method for creating a new instance of this fragment.
     * 
     * @param eventId    Optional event ID to load immediately.
     * @param eventTitle Optional event title to display immediately.
     * @return A new instance of OrganizerManageFragment.
     */
    public static OrganizerManageFragment newInstance(String eventId, String eventTitle) {
        OrganizerManageFragment fragment = new OrganizerManageFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID", eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }
}
