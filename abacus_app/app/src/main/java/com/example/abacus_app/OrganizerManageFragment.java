package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * OrganizerManageFragment
 *
 * UI controller for the organizer's event management screen. Operates in two modes:
 *
 * <ul>
 *   <li><b>EVENT_LIST</b> — shows all events owned by the current organizer. Tapping
 *       an event transitions to WAITLIST mode for that event.</li>
 *   <li><b>WAITLIST</b> — shows the waitlist for the selected event with filtering
 *       by status (waitlisted / invited / accepted / cancelled). Provides buttons to
 *       draw the lottery, draw a replacement, export enrolled entrants to CSV, and
 *       navigate to {@link BrowseEntrantsFragment} to send invites.</li>
 * </ul>
 *
 * <p><b>Direct access (co-organizer):</b> When launched with {@code EVENT_ID} and
 * {@code EVENT_TITLE} arguments (e.g. from the home screen Manage button), the
 * fragment skips the event list and opens the waitlist directly. The back button
 * in this case returns to the Saved screen instead of the event list.</p>
 *
 * <p><b>Send Invites:</b> Tapping the "Send Invites" button navigates to
 * {@link BrowseEntrantsFragment}, passing the selected event's ID, title, and
 * privacy flag so that fragment can offer the correct invite options.</p>
 *
 * Owner: Himesh
 */
public class OrganizerManageFragment extends Fragment {

    private static final String TAG = "OrganizerManageFragment";

    // ── ViewModel & RecyclerView ──────────────────────────────────────────────

    /** ViewModel providing event list, waitlist entries, lottery state, and co-organizers. */
    private ManageEventViewModel viewModel;

    /** Shared RecyclerView — shows event cards in EVENT_LIST mode, waitlist rows in WAITLIST mode. */
    private RecyclerView    recyclerView;

    /** Adapter for waitlist entries in WAITLIST mode. */
    private WaitlistAdapter waitlistAdapter;

    // ── Header views ──────────────────────────────────────────────────────────

    /** Displays "My Events" in EVENT_LIST mode, or the selected event title in WAITLIST mode. */
    private TextView tvEventName;

    /** Shows event/entry counts and loading state. */
    private TextView tvCount;

    // ── Action buttons ────────────────────────────────────────────────────────

    /** Triggers the lottery draw for the selected event. Hidden in EVENT_LIST mode. */
    private Button btnDrawLottery;

    /**
     * Triggers a replacement draw (pulls from the waitlist to fill a declined/cancelled slot).
     * Enabled only when invited+accepted count is below event capacity and waitlisted count > 0.
     * Hidden in EVENT_LIST mode.
     */
    private Button btnDrawReplacement;

    /** Exports the accepted/invited entrant list to a CSV file via the system share sheet. */
    private MaterialButton btnExportCsv;

    // ── Filter ────────────────────────────────────────────────────────────────

    /** Scroll container holding the status filter chips. Hidden in EVENT_LIST mode. */
    private View filterContainer;

    /** Chip group for filtering the waitlist by status. */
    private ChipGroup chipGroupFilter;

    // ── Co-organizer UI ───────────────────────────────────────────────────────

    /**
     * Container showing the horizontal co-organizers list and the "Send Invites" button.
     * Hidden in EVENT_LIST mode; shown in WAITLIST mode.
     */
    private LinearLayout layoutCoOrganizers;

    /**
     * Button labelled "Send Invites". Navigates to {@link BrowseEntrantsFragment}
     * with the selected event's details so the organizer can invite entrants or co-organizers.
     */
    private MaterialButton btnAddCoOrganizer;

    /** Horizontal RecyclerView listing current co-organizers for the selected event. */
    private RecyclerView rvCoOrganizers;

    /** Adapter for the co-organizers horizontal list. */
    private CoOrganizerAdapter coOrganizerAdapter;

    /** Backing list for {@link #coOrganizerAdapter}. */
    private List<User> coOrganizersList = new ArrayList<>();

    // ── State ─────────────────────────────────────────────────────────────────

    /** Current display mode — EVENT_LIST or WAITLIST. */
    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;

    /** Firestore document ID of the currently selected event (WAITLIST mode only). */
    private String selectedEventId;

    /** The currently selected {@link Event} object (WAITLIST mode only). */
    private Event selectedEvent;

    /** Total number of entries on the selected event's waitlist. */
    private int selectedEventWaitlistSize;

    /**
     * True when the fragment was launched directly into a specific event
     * (e.g. by a co-organizer tapping Manage on the home screen).
     * Back navigation differs in this case — returns to the Saved screen
     * rather than the event list.
     */
    private boolean isDirectAccess = false;
    /** Root view of the fragment, held for access in helper methods. */
    private View rootView;

    private OnBackPressedCallback callback;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Inflates the layout, binds all views, wires up button listeners, registers
     * the back-press callback, starts observing the ViewModel, and either opens
     * the event list or jumps directly to a waitlist if arguments were supplied.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.organizer_manage_fragment, container, false);

        viewModel          = new ViewModelProvider(this).get(ManageEventViewModel.class);
        tvEventName        = rootView.findViewById(R.id.tv_event_name);
        tvCount            = rootView.findViewById(R.id.tv_waitlist_count);
        recyclerView       = rootView.findViewById(R.id.rv_waitlist);
        btnDrawLottery     = rootView.findViewById(R.id.btn_draw_lottery);
        filterContainer    = rootView.findViewById(R.id.filter_scroll);
        chipGroupFilter    = rootView.findViewById(R.id.chip_group_filter);
        btnDrawReplacement = rootView.findViewById(R.id.btn_draw_replacement);
        btnExportCsv       = rootView.findViewById(R.id.btn_export_csv);

        layoutCoOrganizers = rootView.findViewById(R.id.layout_co_organizers);
        btnAddCoOrganizer  = rootView.findViewById(R.id.btn_add_co_organizer);
        rvCoOrganizers     = rootView.findViewById(R.id.rv_co_organizers);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCoOrganizers.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        coOrganizerAdapter = new CoOrganizerAdapter(coOrganizersList);
        rvCoOrganizers.setAdapter(coOrganizerAdapter);

        // ── Back button ───────────────────────────────────────────────────────
        ImageButton btnBack = rootView.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST) {
                if (isDirectAccess) {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showFragment(R.id.nav_saved, true);
                    }
                } else {
                    showEventList();
                }
            } else {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showHome();
                }
            }
        });

        // ── Draw lottery ──────────────────────────────────────────────────────
        btnDrawLottery.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                viewModel.drawLottery(selectedEventId);
            }
        });

        // ── Draw replacement ──────────────────────────────────────────────────
        btnDrawReplacement.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                viewModel.drawReplacement(selectedEventId);
            }
        });

        // ── Export CSV ────────────────────────────────────────────────────────
        btnExportCsv.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                exportEnrolledListToCsv();
            }
        });

        // ── Status chip filter ────────────────────────────────────────────────
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        // ── Send Invites ──────────────────────────────────────────────────────
        btnAddCoOrganizer.setOnClickListener(v -> {
            if (selectedEventId == null || selectedEvent == null) return;
            Bundle args = new Bundle();
            args.putString(BrowseEntrantsFragment.ARG_EVENT_ID,    selectedEventId);
            args.putString(BrowseEntrantsFragment.ARG_EVENT_TITLE, tvEventName.getText().toString());
            args.putBoolean(BrowseEntrantsFragment.ARG_IS_PRIVATE, selectedEvent.isPrivate());
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showFragment(
                        R.id.browseEntrantsFragment, false, args);
            }
        });

        // ── System back press ─────────────────────────────────────────────────
        callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isDirectAccess && getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showFragment(R.id.nav_saved, true);
                } else {
                    showEventList();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), callback);
        observeViewModel();

        // Direct-access: skip the event list and open the waitlist immediately
        if (getArguments() != null && getArguments().containsKey("EVENT_ID")) {
            isDirectAccess  = true;
            selectedEventId = getArguments().getString("EVENT_ID");
            String title    = getArguments().getString("EVENT_TITLE", "Event");
            showWaitlist(title, selectedEventId);
        } else if (currentMode == Mode.WAITLIST && selectedEventId != null) {
            // Returning from BrowseEntrantsFragment — restore waitlist
            String title = selectedEvent != null ? selectedEvent.getTitle() : "";
            showWaitlist(title, selectedEventId);
        } else {
            isDirectAccess = false;
            showEventList();
        }

        return rootView;
    }

    // ── Mode transitions ──────────────────────────────────────────────────────

    /**
     * Switches to EVENT_LIST mode.
     *
     * <p>Hides all WAITLIST-only controls, resets header text, and triggers a
     * fresh load of the organizer's event list from the ViewModel.</p>
     */
    private void showEventList() {
        callback.setEnabled(false);
        currentMode = Mode.EVENT_LIST;
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).setBottomNavVisible(true);
        tvEventName.setText("My Events");
        tvCount.setText("");
        rootView.findViewById(R.id.btn_back).setVisibility(View.GONE);
        btnDrawLottery.setVisibility(View.GONE);
        btnExportCsv.setVisibility(View.GONE);
        filterContainer.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);
        layoutCoOrganizers.setVisibility(View.GONE);

        UserLocalDataSource local = new UserLocalDataSource(requireContext());
        String uuid               = local.getUUIDSync();
        String firebaseUid        = com.google.firebase.auth.FirebaseAuth.getInstance()
                .getCurrentUser() != null
                ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uuid != null || firebaseUid != null) {
            viewModel.loadOrganizerEvents(uuid, firebaseUid);
        } else {
            tvCount.setText("Could not load events");
        }
    }

    /**
     * Switches to WAITLIST mode for the given event.
     *
     * <p>Shows all WAITLIST-only controls, resets the entry list, and triggers
     * loads for the waitlist entries, lottery status, and co-organizers.</p>
     *
     * @param eventTitle Display title shown in the header.
     * @param eventId    Firestore document ID of the event whose waitlist to show.
     */
    private void showWaitlist(String eventTitle, String eventId) {
        callback.setEnabled(true);
        currentMode = Mode.WAITLIST;
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).setBottomNavVisible(false);
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");
        rootView.findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        btnDrawLottery.setVisibility(View.VISIBLE);
        btnExportCsv.setVisibility(View.VISIBLE);
        filterContainer.setVisibility(View.VISIBLE);
        chipGroupFilter.check(R.id.chip_all);
        layoutCoOrganizers.setVisibility(View.VISIBLE);
        btnAddCoOrganizer.setText("Send Invites");

        filteredEntries.clear();
        waitlistAdapter = new WaitlistAdapter(filteredEntries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
        viewModel.loadCoOrganizers(eventId);

    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    /**
     * Registers all LiveData observers on the ViewModel.
     *
     * <ul>
     *   <li>{@code getEvents()} — rebuilds the EventAdapter in EVENT_LIST mode.</li>
     *   <li>{@code getEntrants()} — refreshes the waitlist and replacement-draw eligibility
     *       in WAITLIST mode.</li>
     *   <li>{@code getCoOrganizers()} — updates the horizontal co-organizers list.</li>
     *   <li>{@code getError()} — shows a Toast for any error string.</li>
     *   <li>{@code getLotteryCompleted()} — toggles between Draw Lottery / Draw Replacement.</li>
     *   <li>{@code getEventDeleted()} — shows a confirmation Toast after deletion.</li>
     *   <li>{@code getIsLoading()} — disables the active draw button while processing.</li>
     * </ul>
     */
    private void observeViewModel() {

        // ── Event list ─────────────────────────────────────────────────────────
        viewModel.getEvents().observe(getViewLifecycleOwner(), eventList -> {
            if (currentMode != Mode.EVENT_LIST) return;
            if (eventList == null || eventList.isEmpty()) {
                tvCount.setText("No events found");
                recyclerView.setAdapter(null);
                return;
            }
            tvCount.setText(eventList.size() + " event(s)");

            UserLocalDataSource local = new UserLocalDataSource(requireContext());
            String uuid               = local.getUUIDSync();
            String firebaseUid        = com.google.firebase.auth.FirebaseAuth.getInstance()
                    .getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;

            recyclerView.setAdapter(new EventAdapter(
                    eventList,
                    (title, autoJoin) -> {
                        for (Event e : eventList) {
                            if (title.equals(e.getTitle())) {
                                selectedEventId = e.getEventId();
                                selectedEvent   = e;
                                showWaitlist(e.getTitle(), selectedEventId);
                                break;
                            }
                        }
                    },
                    event -> new AlertDialog.Builder(requireContext())
                            .setTitle("Delete Event")
                            .setMessage("Are you sure you want to delete this event? This cannot be undone.")
                            .setPositiveButton("Delete", (dialog, which) ->
                                    viewModel.deleteEvent(event.getEventId(), uuid, firebaseUid))
                            .setNegativeButton("Cancel", null)
                            .show(),
                    true,  // isAdmin — shows delete button on each card
                    true,  // canManageEvents
                    uuid,
                    false  // isGuest
            ));
        });

        // ── Waitlist entries ───────────────────────────────────────────────────
        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (currentMode != Mode.WAITLIST || newEntries == null) return;

            allEntries.clear();
            allEntries.addAll(newEntries);
            selectedEventWaitlistSize = allEntries.size();
            tvCount.setText("Total Entrants: " + selectedEventWaitlistSize);

            long countInvitedAccepted = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus())
                            || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                    .count();
            long countWaitlisted = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus()))
                    .count();

            if (selectedEvent != null && selectedEvent.getEventCapacity() != null) {
                Log.d(TAG, "countInvitedAccepted: " + countInvitedAccepted);
                Log.d(TAG, "cap: "                  + selectedEvent.getEventCapacity());
                Log.d(TAG, "waitlist size: "         + countWaitlisted);
                btnDrawReplacement.setEnabled(
                        countInvitedAccepted < selectedEvent.getEventCapacity()
                                && countWaitlisted > 0);
            }

            applyFilter();
        });

        // ── Co-organizers ──────────────────────────────────────────────────────
        viewModel.getCoOrganizers().observe(getViewLifecycleOwner(), users -> {
            Log.d(TAG, "Observed co-organizers update: " + (users != null ? users.size() : "null"));
            if (users != null) {
                coOrganizersList.clear();
                coOrganizersList.addAll(users);
                coOrganizerAdapter.notifyDataSetChanged();
                if (!coOrganizersList.isEmpty()) {
                    layoutCoOrganizers.setVisibility(View.VISIBLE);
                }
            }
        });

        // ── Errors ─────────────────────────────────────────────────────────────
        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });

        // ── Lottery status ─────────────────────────────────────────────────────
        viewModel.getLotteryCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (currentMode != Mode.WAITLIST) return;
            if (completed != null && completed) {
                showDrawReplacementButton();
            } else if (completed != null) {
                showDrawLotteryButton();
            }
        });

        // ── Event deleted ──────────────────────────────────────────────────────
        viewModel.getEventDeleted().observe(getViewLifecycleOwner(), deleted -> {
            if (deleted != null && deleted) {
                Toast.makeText(getContext(), "Event deleted successfully", Toast.LENGTH_SHORT).show();
            }
        });

        // ── Loading state ──────────────────────────────────────────────────────
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (currentMode != Mode.WAITLIST) return;
            if (btnDrawLottery.getVisibility() == View.VISIBLE) {
                btnDrawLottery.setEnabled(!loading);
                btnDrawLottery.setText(loading ? "Processing..." : "Draw Lottery");
            } else if (btnDrawReplacement.getVisibility() == View.VISIBLE) {
                if (loading) btnDrawReplacement.setEnabled(false);
                btnDrawReplacement.setText(loading ? "Processing..." : "Draw Replacement");
            }
        });
    }

    // ── Waitlist filter ───────────────────────────────────────────────────────

    /**
     * Filters {@link #allEntries} by the currently checked chip and refreshes
     * the waitlist adapter. Updates {@link #tvCount} to show the filtered vs total count.
     *
     * <p>Chip mapping:
     * <ul>
     *   <li>{@code chip_waitlisted} → STATUS_WAITLISTED</li>
     *   <li>{@code chip_invited}    → STATUS_INVITED</li>
     *   <li>{@code chip_accepted}   → STATUS_ACCEPTED</li>
     *   <li>{@code chip_cancelled}  → STATUS_CANCELLED or STATUS_DECLINED</li>
     *   <li>default (chip_all)      → all entries</li>
     * </ul>
     * </p>
     */
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
                    .filter(e -> WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus())
                            || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else {
            result = new ArrayList<>(allEntries);
        }

        filteredEntries.clear();
        filteredEntries.addAll(result);
        tvCount.setText("Showing: " + filteredEntries.size() + " / Total: " + allEntries.size());
        if (waitlistAdapter != null) waitlistAdapter.notifyDataSetChanged();
    }

    // ── CSV Export ────────────────────────────────────────────────────────────

    /**
     * Builds a CSV of all accepted and invited entrants and shares it via the
     * system share sheet.
     *
     * <p>Columns: Name, Email, Status, Registration Time.</p>
     * <p>File is written to the app's cache directory and exposed via
     * {@link androidx.core.content.FileProvider}.</p>
     */
    private void exportEnrolledListToCsv() {
        List<WaitlistEntry> enrolledEntries = allEntries.stream()
                .filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus())
                        || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                .collect(Collectors.toList());

        if (enrolledEntries.isEmpty()) {
            Toast.makeText(getContext(), "No enrolled entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Name,Email,Status,Registration Time\n");
        java.text.SimpleDateFormat sdf =
                new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());

        for (WaitlistEntry entry : enrolledEntries) {
            String name     = entry.getUserName()  != null ? entry.getUserName()  : "Guest";
            String email    = entry.getUserEmail() != null ? entry.getUserEmail() : "";
            String status   = entry.getStatus()    != null ? entry.getStatus()    : "";
            String joinTime = entry.getTimestamp() != null
                    ? sdf.format(new java.util.Date(entry.getTimestamp())) : "Unknown";
            csvContent.append("\"").append(escapeQuotes(name)).append("\",")
                    .append("\"").append(escapeQuotes(email)).append("\",")
                    .append("\"").append(escapeQuotes(status)).append("\",")
                    .append("\"").append(escapeQuotes(joinTime)).append("\"\n");
        }

        String fileName = (selectedEvent != null ? selectedEvent.getTitle() : "entrants")
                + "_" + System.currentTimeMillis() + ".csv";

        try {
            java.io.File file = new java.io.File(getContext().getCacheDir(), fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(csvContent.toString());
            writer.close();

            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".fileprovider",
                    file);

            android.content.Intent shareIntent =
                    new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT,
                    "Enrolled Entrants - " + fileName);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Export CSV"));

        } catch (Exception e) {
            Log.e(TAG, "Error exporting CSV", e);
            Toast.makeText(getContext(),
                    "Error exporting CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Escapes double-quote characters in a CSV field value by doubling them,
     * per RFC 4180.
     *
     * @param value The raw field value; may be null.
     * @return The escaped string, or an empty string if {@code value} is null.
     */
    private String escapeQuotes(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }

    // ── Button visibility helpers ─────────────────────────────────────────────

    /**
     * Shows the Draw Lottery button and hides the Draw Replacement button.
     * Called when {@code getLotteryCompleted()} emits {@code false}.
     */
    private void showDrawLotteryButton() {
        if (btnDrawLottery     != null) { btnDrawLottery.setVisibility(View.VISIBLE); btnDrawLottery.setEnabled(true); }
        if (btnDrawReplacement != null)   btnDrawReplacement.setVisibility(View.GONE);
    }

    /**
     * Shows the Draw Replacement button (initially disabled) and hides the
     * Draw Lottery button. Called when {@code getLotteryCompleted()} emits {@code true}.
     * The button is re-enabled by the entrants observer once eligibility is confirmed.
     */
    private void showDrawReplacementButton() {
        if (btnDrawLottery     != null)   btnDrawLottery.setVisibility(View.GONE);
        if (btnDrawReplacement != null) { btnDrawReplacement.setVisibility(View.VISIBLE); btnDrawReplacement.setEnabled(false); }
    }

    // ── Static factory ────────────────────────────────────────────────────────

    /**
     * Creates a new {@code OrganizerManageFragment} pre-loaded for a specific event.
     * Used when navigating directly from the home screen (co-organizer Manage button).
     *
     * @param eventId    Firestore document ID of the event to open.
     * @param eventTitle Display title passed to the waitlist header.
     * @return A configured fragment instance.
     */
    public static OrganizerManageFragment newInstance(String eventId, String eventTitle) {
        OrganizerManageFragment fragment = new OrganizerManageFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID",    eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }

    // ── Backing lists (used by adapters) ──────────────────────────────────────

    /** All waitlist entries for the selected event, unfiltered. */
    private List<WaitlistEntry> allEntries      = new ArrayList<>();

    /** Currently displayed subset of {@link #allEntries} after chip filtering. */
    private List<WaitlistEntry> filteredEntries = new ArrayList<>();
}