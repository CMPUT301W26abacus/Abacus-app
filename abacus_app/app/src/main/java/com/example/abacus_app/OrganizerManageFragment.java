package com.example.abacus_app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/**
 * UI Controller for the active event management screen.
 * Shows the organizer's events, then the waitlist for a selected event.
 * Owner: Himesh
 */
public class OrganizerManageFragment extends Fragment {

    private static final String TAG = "OrganizerManageFragment";
    private ManageEventViewModel viewModel;
    private RecyclerView recyclerView;
    private WaitlistAdapter waitlistAdapter;
    private TextView tvEventName, tvCount;
    private Button btnDrawLottery;
    private Button btnDrawReplacement;

    // Co-organizer UI
    private LinearLayout layoutCoOrganizers;
    private MaterialButton btnAddCoOrganizer;
    private LinearLayout layoutSearchCoOrganizer;
    private TextInputEditText etSearchEntrant;
    private RecyclerView rvSearchResults;
    private RecyclerView rvCoOrganizers;
    private UserSearchAdapter searchAdapter;
    private CoOrganizerAdapter coOrganizerAdapter;

    private List<WaitlistEntry> entries = new ArrayList<>();
    private List<User> searchResultsList = new ArrayList<>();
    private List<User> coOrganizersList = new ArrayList<>();

    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;
    private String selectedEventId;
    private Event selectedEvent;
    private int selectedEventWaitlistSize;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.organizer_manage_fragment, container, false);
        viewModel      = new ViewModelProvider(this).get(ManageEventViewModel.class);
        tvEventName    = view.findViewById(R.id.tv_event_name);
        tvCount        = view.findViewById(R.id.tv_waitlist_count);
        recyclerView   = view.findViewById(R.id.rv_waitlist);
        btnDrawLottery = view.findViewById(R.id.btn_draw_lottery);
        btnDrawReplacement = view.findViewById(R.id.btn_draw_replacement);

        // Co-organizer UI binding
        layoutCoOrganizers = view.findViewById(R.id.layout_co_organizers);
        btnAddCoOrganizer = view.findViewById(R.id.btn_add_co_organizer);
        layoutSearchCoOrganizer = view.findViewById(R.id.layout_search_co_organizer);
        etSearchEntrant = view.findViewById(R.id.et_search_entrant);
        rvSearchResults = view.findViewById(R.id.rv_search_results);
        rvCoOrganizers = view.findViewById(R.id.rv_co_organizers);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCoOrganizers.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        rvSearchResults.setLayoutManager(new LinearLayoutManager(getContext()));

        // Setup search adapter
        searchAdapter = new UserSearchAdapter(searchResultsList, user -> {
            if (selectedEventId != null) {
                Log.d(TAG, "Adding co-organizer: " + user.getUid() + " to event: " + selectedEventId);
                viewModel.addCoOrganizer(selectedEventId, user);
                etSearchEntrant.setText("");
                layoutSearchCoOrganizer.setVisibility(View.GONE);
                rvSearchResults.setVisibility(View.GONE);
            }
        });
        rvSearchResults.setAdapter(searchAdapter);

        // Setup co-organizers adapter
        coOrganizerAdapter = new CoOrganizerAdapter(coOrganizersList);
        rvCoOrganizers.setAdapter(coOrganizerAdapter);

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

        btnDrawLottery.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                viewModel.drawLottery(selectedEventId);
            }
        });

        btnDrawReplacement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                    viewModel.drawReplacement(selectedEventId);
                }
            }
        });

        btnAddCoOrganizer.setOnClickListener(v -> {
            if (layoutSearchCoOrganizer.getVisibility() == View.GONE) {
                layoutSearchCoOrganizer.setVisibility(View.VISIBLE);
                etSearchEntrant.requestFocus();
            } else {
                layoutSearchCoOrganizer.setVisibility(View.GONE);
                rvSearchResults.setVisibility(View.GONE);
                etSearchEntrant.setText("");
            }
        });

        etSearchEntrant.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 3) {
                    viewModel.searchUsersByEmail(query);
                } else {
                    searchResultsList.clear();
                    searchAdapter.notifyDataSetChanged();
                    rvSearchResults.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        observeViewModel();
        showEventList();
        return view;
    }

    private void showEventList() {
        currentMode = Mode.EVENT_LIST;
        tvEventName.setText("My Events");
        tvCount.setText("");
        btnDrawLottery.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);
        layoutCoOrganizers.setVisibility(View.GONE);

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

            recyclerView.setAdapter(new EventAdapter(eventList, (title, autoJoin) -> {
                for (Event e : eventList) {
                    if (title.equals(e.getTitle())) {
                        selectedEventId = e.getEventId();
                        selectedEvent = e;
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
                selectedEventWaitlistSize = entries.size();
                tvCount.setText("Total Entrants: " + selectedEventWaitlistSize);
                waitlistAdapter.notifyDataSetChanged();

                long countInvitedAccepted = entries.stream()
                        .filter(entry -> (entry.getStatus().equals(WaitlistEntry.STATUS_INVITED) || entry.getStatus().equals(WaitlistEntry.STATUS_ACCEPTED)))
                        .count();
                
                if (selectedEvent != null && selectedEvent.getEventCapacity() != null) {
                    if (countInvitedAccepted < Math.min(selectedEvent.getEventCapacity(), selectedEventWaitlistSize)) {
                        btnDrawReplacement.setEnabled(true);
                    } else {
                        btnDrawReplacement.setEnabled(false);
                    }
                }
            }
        });

        // Search results
        viewModel.getSearchResults().observe(getViewLifecycleOwner(), users -> {
            if (users != null && !users.isEmpty()) {
                searchResultsList.clear();
                searchResultsList.addAll(users);
                searchAdapter.notifyDataSetChanged();
                rvSearchResults.setVisibility(View.VISIBLE);
            } else {
                searchResultsList.clear();
                searchAdapter.notifyDataSetChanged();
                rvSearchResults.setVisibility(View.GONE);
            }
        });

        // Co-organizers
        viewModel.getCoOrganizers().observe(getViewLifecycleOwner(), users -> {
            Log.d(TAG, "Observed co-organizers update: " + (users != null ? users.size() : "null"));
            if (users != null) {
                coOrganizersList.clear();
                coOrganizersList.addAll(users);
                coOrganizerAdapter.notifyDataSetChanged();
                
                // Ensure the layout is visible if there are co-organizers
                if (!coOrganizersList.isEmpty()) {
                    layoutCoOrganizers.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });

        viewModel.getLotteryCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (currentMode == Mode.WAITLIST) {
                if (completed != null && completed) {
                    showDrawReplacementButton();
                } else if (completed != null && !completed) {
                    showDrawLotteryButton();
                }
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (currentMode == Mode.WAITLIST) {
                if (btnDrawLottery.getVisibility() == View.VISIBLE) {
                    btnDrawLottery.setEnabled(!loading);
                    btnDrawLottery.setText(loading ? "Processing..." : "Draw Lottery");
                } else if (btnDrawReplacement.getVisibility() == View.VISIBLE) {
                    btnDrawReplacement.setEnabled(false);
                    btnDrawReplacement.setText(loading ? "Processing..." : "Draw Replacement");
                }
            }
        });
    }

    private void showWaitlist(String eventTitle, String eventId) {
        currentMode = Mode.WAITLIST;
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");
        layoutCoOrganizers.setVisibility(View.VISIBLE);
        layoutSearchCoOrganizer.setVisibility(View.GONE);

        waitlistAdapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
        viewModel.loadCoOrganizers(eventId);
    }

    public static OrganizerManageFragment newInstance(String eventId, String eventTitle) {
        OrganizerManageFragment fragment = new OrganizerManageFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID", eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }

    // ── Button visibility ─────────────────────────────────────────────────────

    private void showDrawLotteryButton() {
        if (btnDrawLottery  != null) btnDrawLottery.setVisibility(View.VISIBLE);
        if (btnDrawReplacement != null) btnDrawReplacement.setVisibility(View.GONE);
        if (btnDrawLottery  != null) btnDrawLottery.setEnabled(true);
    }

    private void showDrawReplacementButton() {
        if (btnDrawLottery  != null) btnDrawLottery.setVisibility(View.GONE);
        if (btnDrawReplacement != null) btnDrawReplacement.setVisibility(View.VISIBLE);
        if (btnDrawReplacement != null) btnDrawReplacement.setEnabled(false);
    }
}
