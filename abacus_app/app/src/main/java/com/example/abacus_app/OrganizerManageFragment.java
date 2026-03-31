package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
    private Button btnDrawLottery;
    private Button btnDrawReplacement;
    private View filterContainer;
    private ChipGroup chipGroupFilter;

    private List<WaitlistEntry> allEntries = new ArrayList<>();
    private List<WaitlistEntry> filteredEntries = new ArrayList<>();

    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;
    private String selectedEventId;
    private Event selectedEvent;

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
        filterContainer = view.findViewById(R.id.filter_scroll);
        chipGroupFilter = view.findViewById(R.id.chip_group_filter);
        btnDrawReplacement = view.findViewById(R.id.btn_draw_replacement);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

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

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            applyFilter();
        });

        observeViewModel();
        showEventList();

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (currentMode == Mode.WAITLIST) {
                            showEventList();
                        } else if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
                        }
                    }
                });

        return view;
    }

    private void showEventList() {
        currentMode = Mode.EVENT_LIST;
        tvEventName.setText("My Events");
        tvCount.setText("");
        btnDrawLottery.setVisibility(View.GONE);
        filterContainer.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);

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

            UserLocalDataSource local = new UserLocalDataSource(requireContext());
            String uuid = local.getUUIDSync();

            recyclerView.setAdapter(new EventAdapter(
                    eventList,
                    (title, autoJoin) -> {
                        for (Event e : eventList) {
                            if (title.equals(e.getTitle())) {
                                selectedEventId = e.getEventId();
                                selectedEvent = e;
                                showWaitlist(e.getTitle(), selectedEventId);
                                break;
                            }
                        }
                    },
                    event -> {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete Event")
                                .setMessage("Are you sure you want to delete this event? This cannot be undone.")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    viewModel.deleteEvent(event.getEventId(), uuid);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    },
                    true, // isAdmin (to show delete button)
                    uuid,
                    false
            ));
        });

        // Waitlist mode
        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (currentMode != Mode.WAITLIST || newEntries == null) return;

            // Update the main list
            allEntries.clear();
            allEntries.addAll(newEntries);

            // Update replacement/draw button logic
            int waitlistSize = allEntries.size();
            tvCount.setText("Total Entrants: " + waitlistSize);

            long countInvitedAccepted = allEntries.stream()
                    .filter(entry -> WaitlistEntry.STATUS_INVITED.equals(entry.getStatus())
                            || WaitlistEntry.STATUS_ACCEPTED.equals(entry.getStatus()))
                    .count();

            if (selectedEvent != null && countInvitedAccepted < Math.min(selectedEvent.getEventCapacity(), waitlistSize)) {
                btnDrawReplacement.setEnabled(true);
            } else {
                btnDrawReplacement.setEnabled(false);
            }

            // Apply the filter for UI
            applyFilter();
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

        viewModel.getEventDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (deleted != null && deleted) {
                Toast.makeText(getContext(), "Event deleted successfully", Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (currentMode == Mode.WAITLIST) {
                if (btnDrawLottery.getVisibility() == View.VISIBLE) {
                    btnDrawLottery.setEnabled(!loading);
                    btnDrawLottery.setText(loading ? "Processing..." : "Draw Lottery");
                } else if (btnDrawReplacement.getVisibility() == View.VISIBLE) {
                    if (loading) {
                        btnDrawReplacement.setEnabled(false);
                    }
                    btnDrawReplacement.setText(loading ? "Processing..." : "Draw Replacement");
                }
            }
        });
    }

    private void showWaitlist(String eventTitle, String eventId) {
        currentMode = Mode.WAITLIST;
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");
        btnDrawLottery.setVisibility(View.VISIBLE);
        filterContainer.setVisibility(View.VISIBLE);
        chipGroupFilter.check(R.id.chip_all);

        filteredEntries.clear();
        waitlistAdapter = new WaitlistAdapter(filteredEntries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
    }

    private void applyFilter() {
        int checkedId = chipGroupFilter.getCheckedChipId();
        List<WaitlistEntry> result;

        if (checkedId == R.id.chip_waitlisted) {
            result = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else if (checkedId == R.id.chip_invited) {
            result = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else if (checkedId == R.id.chip_accepted) {
            result = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else if (checkedId == R.id.chip_cancelled) {
            result = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus()) || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else {
            result = new ArrayList<>(allEntries);
        }

        filteredEntries.clear();
        filteredEntries.addAll(result);
        tvCount.setText("Showing: " + filteredEntries.size() + " / Total: " + allEntries.size());
        if (waitlistAdapter != null) {
            waitlistAdapter.notifyDataSetChanged();
        }
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
    }
}