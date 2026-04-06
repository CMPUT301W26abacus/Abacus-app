package com.example.abacus_app;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * OrganizerManageFragment
 *
 * UI controller for the organizer's event management screen. Operates in two modes:
 *
 * <ul>
 *   <li><b>EVENT_LIST</b> — shows all events owned by the current organizer.</li>
 *   <li><b>WAITLIST</b> — shows the waitlist for the selected event with filtering
 *       and manual notification capabilities.</li>
 * </ul>
 *
 * Owner: Himesh
 */
public class OrganizerManageFragment extends Fragment {

    private static final String TAG = "OrganizerManageFragment";

    private ManageEventViewModel viewModel;
    private RecyclerView    recyclerView;
    private WaitlistAdapter waitlistAdapter;

    private TextView tvEventName;
    private TextView tvCount;

    private Button btnDrawLottery;
    private Button btnDrawReplacement;
    private MaterialButton btnExportCsv;
    private MaterialButton btnNotify;

    private View filterContainer;
    private ChipGroup chipGroupFilter;

    private LinearLayout layoutCoOrganizers;
    private LinearLayout layoutCoOrganizerStrip;
    private MaterialButton btnAddCoOrganizer;
    private RecyclerView rvCoOrganizers;
    private CoOrganizerAdapter coOrganizerAdapter;
    private List<User> coOrganizersList = new ArrayList<>();

    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;

    private String selectedEventId;
    private Event selectedEvent;
    private int selectedEventWaitlistSize;
    private boolean isDirectAccess = false;
    private View rootView;
    private androidx.swiperefreshlayout.widget.SwipeRefreshLayout swipeRefresh;
    private OnBackPressedCallback callback;

    private List<WaitlistEntry> allEntries      = new ArrayList<>();
    private List<WaitlistEntry> filteredEntries = new ArrayList<>();
    private View    bannerEventEnded;
    private boolean isEventOver = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.organizer_manage_fragment, container, false);

        viewModel          = new ViewModelProvider(this).get(ManageEventViewModel.class);
        swipeRefresh       = rootView.findViewById(R.id.swipe_refresh);
        tvEventName        = rootView.findViewById(R.id.tv_event_name);
        tvCount            = rootView.findViewById(R.id.tv_waitlist_count);
        recyclerView       = rootView.findViewById(R.id.rv_waitlist);
        btnDrawLottery     = rootView.findViewById(R.id.btn_draw_lottery);
        filterContainer    = rootView.findViewById(R.id.filter_scroll);
        chipGroupFilter    = rootView.findViewById(R.id.chip_group_filter);
        btnDrawReplacement = rootView.findViewById(R.id.btn_draw_replacement);
        btnExportCsv       = rootView.findViewById(R.id.btn_export_csv);
        btnNotify          = rootView.findViewById(R.id.btn_notify_entrants);
        bannerEventEnded   = rootView.findViewById(R.id.banner_event_ended);

        layoutCoOrganizers = rootView.findViewById(R.id.layout_co_organizers);
        layoutCoOrganizerStrip = rootView.findViewById(R.id.layout_co_organizer_strip);
        btnAddCoOrganizer  = rootView.findViewById(R.id.btn_add_co_organizer);
        rvCoOrganizers     = rootView.findViewById(R.id.rv_co_organizers);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        rvCoOrganizers.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

        coOrganizerAdapter = new CoOrganizerAdapter(coOrganizersList);
        rvCoOrganizers.setAdapter(coOrganizerAdapter);

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

        btnDrawLottery.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                viewModel.drawLottery(selectedEventId);
            }
        });

        btnDrawReplacement.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                viewModel.drawReplacement(selectedEventId);
            }
        });

        btnExportCsv.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) {
                exportEnrolledListToCsv();
            }
        });

        btnNotify.setOnClickListener(v -> showNotifyCategoryDialog());

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

        btnAddCoOrganizer.setOnClickListener(v -> {
            if (selectedEventId == null || selectedEvent == null) return;
            if (isEventOver) {
                Toast.makeText(getContext(),
                        "This event has ended. No invites can be sent.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Bundle args = new Bundle();
            args.putString(BrowseEntrantsFragment.ARG_EVENT_ID,    selectedEventId);
            args.putString(BrowseEntrantsFragment.ARG_EVENT_TITLE, tvEventName.getText().toString());
            args.putBoolean(BrowseEntrantsFragment.ARG_IS_PRIVATE, selectedEvent.isPrivate());
            args.putLong(BrowseEntrantsFragment.ARG_REG_END_MS,
                    selectedEvent.getRegistrationEnd() != null
                            ? selectedEvent.getRegistrationEnd().toDate().getTime() : 0L);
            args.putLong(BrowseEntrantsFragment.ARG_EVENT_END_MS,
                    selectedEvent.getEventEnd() != null
                            ? selectedEvent.getEventEnd().toDate().getTime() : 0L);
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showFragment(
                        R.id.browseEntrantsFragment, false, args);
            }
        });

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

        if (getArguments() != null && getArguments().containsKey("EVENT_ID")) {
            isDirectAccess  = true;
            selectedEventId = getArguments().getString("EVENT_ID");
            String title    = getArguments().getString("EVENT_TITLE", "Event");
            showWaitlist(title, selectedEventId);
        } else if (currentMode == Mode.WAITLIST && selectedEventId != null) {
            String title = selectedEvent != null ? selectedEvent.getTitle() : "";
            showWaitlist(title, selectedEventId);
        } else {
            isDirectAccess = false;
            showEventList();
        }

        return rootView;
    }

    private void showEventList() {
        callback.setEnabled(false);
        currentMode = Mode.EVENT_LIST;
        isEventOver = false;
        if (bannerEventEnded != null) bannerEventEnded.setVisibility(View.GONE);
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).setBottomNavVisible(true);
        tvEventName.setText("My Events");
        tvCount.setText("");
        rootView.findViewById(R.id.btn_back).setVisibility(View.GONE);
        btnDrawLottery.setVisibility(View.GONE);
        btnExportCsv.setVisibility(View.GONE);
        btnNotify.setVisibility(View.GONE);
        filterContainer.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);
        layoutCoOrganizers.setVisibility(View.GONE);
        layoutCoOrganizerStrip.setVisibility(View.GONE);

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

        swipeRefresh.setOnRefreshListener(() -> {
            String refreshUuid = new UserLocalDataSource(requireContext()).getUUIDSync();
            String refreshUid  = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null
                    ? com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getUid()
                    : null;
            if (refreshUuid != null || refreshUid != null) {
                viewModel.loadOrganizerEvents(refreshUuid, refreshUid);
            }
            swipeRefresh.setRefreshing(false);
        });
    }

    private void showWaitlist(String eventTitle, String eventId) {
        callback.setEnabled(true);
        currentMode = Mode.WAITLIST;
        isEventOver = false;
        if (bannerEventEnded != null) bannerEventEnded.setVisibility(View.GONE);
        if (getActivity() instanceof MainActivity)
            ((MainActivity) getActivity()).setBottomNavVisible(false);
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");
        rootView.findViewById(R.id.btn_back).setVisibility(View.VISIBLE);
        btnDrawLottery.setVisibility(View.VISIBLE);
        btnExportCsv.setVisibility(View.VISIBLE);
        btnNotify.setVisibility(View.VISIBLE);
        filterContainer.setVisibility(View.VISIBLE);
        chipGroupFilter.check(R.id.chip_all);
        layoutCoOrganizers.setVisibility(View.VISIBLE);
        layoutCoOrganizerStrip.setVisibility(View.GONE);
        btnAddCoOrganizer.setText("Send Invites");

        filteredEntries.clear();
        waitlistAdapter = new WaitlistAdapter(filteredEntries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
        viewModel.loadCoOrganizers(eventId);
    }

    private void observeViewModel() {
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
                    true, true, uuid, false
            ));
        });

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
                btnDrawReplacement.setEnabled(
                        countInvitedAccepted < selectedEvent.getEventCapacity() && countWaitlisted > 0);
            }
            applyFilter();
        });

        viewModel.getCoOrganizers().observe(getViewLifecycleOwner(), users -> {
            if (users != null) {
                coOrganizersList.clear();
                coOrganizersList.addAll(users);
                coOrganizerAdapter.notifyDataSetChanged();
                layoutCoOrganizerStrip.setVisibility(
                        coOrganizersList.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });

        viewModel.getLotteryCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (currentMode != Mode.WAITLIST) return;
            if (completed != null && completed) showDrawReplacementButton();
            else if (completed != null) showDrawLotteryButton();
        });

        viewModel.getEventDetails().observe(getViewLifecycleOwner(), event -> {
            if (currentMode != Mode.WAITLIST || event == null) return;
            selectedEvent = event;

            long now = System.currentTimeMillis();
            isEventOver = event.getEventEnd() != null
                    && now > event.getEventEnd().toDate().getTime();

            if (bannerEventEnded != null) {
                bannerEventEnded.setVisibility(isEventOver ? View.VISIBLE : View.GONE);
            }
            if (isEventOver) {
                btnDrawLottery.setVisibility(View.GONE);
                btnDrawReplacement.setVisibility(View.GONE);
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (currentMode != Mode.WAITLIST) return;
            if (btnDrawLottery.getVisibility() == View.VISIBLE) {
                btnDrawLottery.setEnabled(!loading);
                btnDrawLottery.setText(loading ? "Processing..." : "Draw Lottery");
            } else if (btnDrawReplacement.getVisibility() == View.VISIBLE) {
                btnDrawReplacement.setEnabled(!loading);
                btnDrawReplacement.setText(loading ? "Processing..." : "Draw Replacement");
            }
        });
    }

    private void applyFilter() {
        int checkedId = chipGroupFilter.getCheckedChipId();
        List<WaitlistEntry> result;
        if (checkedId == R.id.chip_waitlisted) {
            result = allEntries.stream().filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus())).collect(Collectors.toList());
        } else if (checkedId == R.id.chip_invited) {
            result = allEntries.stream().filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus())).collect(Collectors.toList());
        } else if (checkedId == R.id.chip_accepted) {
            result = allEntries.stream().filter(e -> WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus())).collect(Collectors.toList());
        } else if (checkedId == R.id.chip_cancelled) {
            result = allEntries.stream().filter(e -> WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus()) || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus())).collect(Collectors.toList());
        } else {
            result = new ArrayList<>(allEntries);
        }
        filteredEntries.clear();
        filteredEntries.addAll(result);
        tvCount.setText("Showing: " + filteredEntries.size() + " / Total: " + allEntries.size());
        if (waitlistAdapter != null) waitlistAdapter.notifyDataSetChanged();
    }

    private void exportEnrolledListToCsv() {
        List<WaitlistEntry> enrolledEntries = allEntries.stream()
                .filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus()) || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                .collect(Collectors.toList());
        if (enrolledEntries.isEmpty()) {
            Toast.makeText(getContext(), "No enrolled entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Name,Email,Status,Registration Time\n");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault());
        for (WaitlistEntry entry : enrolledEntries) {
            String name = entry.getUserName() != null ? entry.getUserName() : "Guest";
            String email = entry.getUserEmail() != null ? entry.getUserEmail() : "";
            String status = entry.getStatus() != null ? entry.getStatus() : "";
            String joinTime = entry.getTimestamp() != null ? sdf.format(new java.util.Date(entry.getTimestamp())) : "Unknown";
            csvContent.append("\"").append(escapeQuotes(name)).append("\",")
                    .append("\"").append(escapeQuotes(email)).append("\",")
                    .append("\"").append(escapeQuotes(status)).append("\",")
                    .append("\"").append(escapeQuotes(joinTime)).append("\"\n");
        }
        String fileName = (selectedEvent != null ? selectedEvent.getTitle() : "entrants") + "_" + System.currentTimeMillis() + ".csv";
        try {
            java.io.File file = new java.io.File(getContext().getCacheDir(), fileName);
            java.io.FileWriter writer = new java.io.FileWriter(file);
            writer.write(csvContent.toString());
            writer.close();
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
            android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Enrolled Entrants - " + fileName);
            shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(shareIntent, "Export CSV"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error exporting CSV: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String escapeQuotes(String value) { return value == null ? "" : value.replace("\"", "\"\""); }

    private void showDrawLotteryButton() {
        if (isEventOver) {
            if (btnDrawLottery != null) btnDrawLottery.setVisibility(View.GONE);
            if (btnDrawReplacement != null) btnDrawReplacement.setVisibility(View.GONE);
            return;
        }
        if (btnDrawLottery != null) { btnDrawLottery.setVisibility(View.VISIBLE); btnDrawLottery.setEnabled(true); }
        if (btnDrawReplacement != null) btnDrawReplacement.setVisibility(View.GONE);
    }

    private void showDrawReplacementButton() {
        if (isEventOver) {
            if (btnDrawLottery != null) btnDrawLottery.setVisibility(View.GONE);
            if (btnDrawReplacement != null) btnDrawReplacement.setVisibility(View.GONE);
            return;
        }
        if (btnDrawLottery != null) btnDrawLottery.setVisibility(View.GONE);
        if (btnDrawReplacement != null) { btnDrawReplacement.setVisibility(View.VISIBLE); btnDrawReplacement.setEnabled(false); }
    }

    // ── Notify Flow ──────────────────────────────────────────────────────────

    private void showNotifyCategoryDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_notify_category, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();

        dialogView.findViewById(R.id.btn_category_waitlist).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog(WaitlistEntry.STATUS_WAITLISTED);
        });
        dialogView.findViewById(R.id.btn_category_selected).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog("selected");
        });
        dialogView.findViewById(R.id.btn_category_cancelled).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog("cancelled");
        });
        dialog.show();
    }

    private void showSelectEntrantsDialog(String status) {
        List<WaitlistEntry> targetEntrants = allEntries.stream().filter(e -> {
            if ("selected".equals(status)) return WaitlistEntry.STATUS_INVITED.equals(e.getStatus()) || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus());
            if ("cancelled".equals(status)) return WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus()) || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus());
            return status.equals(e.getStatus());
        }).collect(Collectors.toList());

        if (targetEntrants.isEmpty()) {
            Toast.makeText(getContext(), "No entrants in this category", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_select_entrants, null);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cb_select_all);
        RecyclerView rv = dialogView.findViewById(R.id.rv_entrants_selection);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        EntrantSelectionAdapter selectionAdapter = new EntrantSelectionAdapter(targetEntrants);
        rv.setAdapter(selectionAdapter);

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> selectionAdapter.selectAll(isChecked));

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Next", (d, w) -> {
                    List<String> selectedIds = selectionAdapter.getSelectedUserIds();
                    if (selectedIds.isEmpty()) Toast.makeText(getContext(), "No one selected", Toast.LENGTH_SHORT).show();
                    else showComposeMessageDialog(selectedIds);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showComposeMessageDialog(List<String> selectedUserIds) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_compose_message, null);
        TextView tvCount = dialogView.findViewById(R.id.tv_recipient_count);
        TextInputEditText etMessage = dialogView.findViewById(R.id.et_message);
        tvCount.setText("Sending to " + selectedUserIds.size() + " recipient(s)");

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Send", (d, w) -> {
                    String msg = etMessage.getText().toString();
                    if (msg.trim().isEmpty()) Toast.makeText(getContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
                    else {
                        viewModel.sendManualNotifications(selectedEventId, selectedUserIds, msg);
                        Toast.makeText(getContext(), "Notifications sent", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── EntrantSelectionAdapter (Inner Class) ────────────────────────────────

    private static class EntrantSelectionAdapter extends RecyclerView.Adapter<EntrantSelectionAdapter.ViewHolder> {
        private final List<WaitlistEntry> entrants;
        private final Set<String> selectedUserIds = new HashSet<>();

        EntrantSelectionAdapter(List<WaitlistEntry> entrants) { this.entrants = entrants; }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entrant_selection, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WaitlistEntry entry = entrants.get(position);
            holder.tvName.setText(entry.getUserName() != null ? entry.getUserName() : "Anonymous");
            holder.tvEmail.setText(entry.getUserEmail() != null ? entry.getUserEmail() : "");
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.checkBox.setChecked(selectedUserIds.contains(entry.getUserId()));
            holder.checkBox.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selectedUserIds.add(entry.getUserId());
                else selectedUserIds.remove(entry.getUserId());
            });
            holder.itemView.setOnClickListener(v -> holder.checkBox.performClick());
        }

        @Override public int getItemCount() { return entrants.size(); }

        void selectAll(boolean selectAll) {
            selectedUserIds.clear();
            if (selectAll) for (WaitlistEntry e : entrants) selectedUserIds.add(e.getUserId());
            notifyDataSetChanged();
        }

        List<String> getSelectedUserIds() { return new ArrayList<>(selectedUserIds); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView tvName, tvEmail;
            ViewHolder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.cb_entrant_selected);
                tvName = itemView.findViewById(R.id.tv_entrant_name);
                tvEmail = itemView.findViewById(R.id.tv_entrant_email);
            }
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
}
