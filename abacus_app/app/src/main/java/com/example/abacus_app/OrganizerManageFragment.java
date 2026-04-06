package com.example.abacus_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UI Controller for the active event management screen.
 * Shows the organizer's events, then the waitlist for a selected event.
 * Owner: Himesh
 *
 * @author Himesh, Kaylee
 */
public class OrganizerManageFragment extends Fragment {

    private static final String TAG = "OrganizerManageFragment";
    private ManageEventViewModel viewModel;
    private RecyclerView recyclerView;
    private WaitlistAdapter waitlistAdapter;
    private TextView tvEventName, tvCount;
    private Button btnDrawLottery;
    private Button btnDrawReplacement;
    private View filterContainer;
    private ChipGroup chipGroupFilter;
    private MaterialButton btnExportCsv;
    private MaterialButton btnNotifyEntrants;

    private List<WaitlistEntry> allEntries = new ArrayList<>();
    private List<WaitlistEntry> filteredEntries = new ArrayList<>();
    // Co-organizer UI
    private LinearLayout layoutCoOrganizers;
    private MaterialButton btnAddCoOrganizer;
    private LinearLayout layoutSearchCoOrganizer;
    private TextInputEditText etSearchEntrant;
    private RecyclerView rvSearchResults;
    private RecyclerView rvCoOrganizers;
    private UserSearchAdapter searchAdapter;
    private CoOrganizerAdapter coOrganizerAdapter;

    private List<User> searchResultsList = new ArrayList<>();
    private List<User> coOrganizersList = new ArrayList<>();

    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;
    private String selectedEventId;
    private Event selectedEvent;
    private int selectedEventWaitlistSize;
    private boolean isDirectAccess = false; // Flag for co-organizer direct access

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
        btnExportCsv = view.findViewById(R.id.btn_export_csv);
        btnNotifyEntrants = view.findViewById(R.id.btn_notify_entrants);

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
                Log.d(TAG, "Inviting co-organizer: " + user.getUid() + " to event: " + selectedEventId);
                viewModel.sendCoOrganizerInvite(selectedEventId, tvEventName.getText().toString(), user);
                Toast.makeText(getContext(), "Invitation sent to " + user.getName(), Toast.LENGTH_SHORT).show();
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
                if (isDirectAccess) {
                    // Co-organizer came directly from home/saved
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).showFragment(R.id.nav_saved, true);
                    }
                } else {
                    // Primary organizer viewing an event waitlist
                    showEventList();
                }
            } else {
                // Primary organizer at the "My Events" list level
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

        btnExportCsv.setOnClickListener(v -> exportEnrolledListToCsv());

        btnNotifyEntrants.setOnClickListener(v -> showNotifyCategoryDialog());

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            applyFilter();
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

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (currentMode == Mode.WAITLIST) {
                            if (isDirectAccess && getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).showFragment(R.id.nav_saved, true);
                            } else {
                                showEventList();
                            }
                        } else if (currentMode == Mode.EVENT_LIST) {
                            // Return to Tools/Organizer home
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
                            }
                        }
                    }
                });


        // Check if arguments were passed (from co-organizer direct access)
        if (getArguments() != null && getArguments().containsKey("EVENT_ID")) {
            isDirectAccess = true;
            selectedEventId = getArguments().getString("EVENT_ID");
            String title = getArguments().getString("EVENT_TITLE", "Event");
            showWaitlist(title, selectedEventId);
        } else {
            isDirectAccess = false;
            showEventList();
        }

        return view;
    }

    private void showEventList() {
        currentMode = Mode.EVENT_LIST;
        tvEventName.setText("My Events");
        tvCount.setText("");
        btnDrawLottery.setVisibility(View.GONE);
        filterContainer.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);
        layoutCoOrganizers.setVisibility(View.GONE);
        btnExportCsv.setVisibility(View.GONE);
        btnNotifyEntrants.setVisibility(View.GONE);

        // Load events by Firebase UID (organizers use Firebase UID as organizerId)
        com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser != null) {
            viewModel.loadOrganizerEvents(firebaseUser.getUid());
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
            com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            String firebaseUid = (fbUser != null) ? fbUser.getUid() : null;

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
                                    viewModel.deleteEvent(event.getEventId(), uuid, firebaseUid);
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    },
                    true,  // isAdmin
                    false, // canManageEvents
                    uuid,
                    false  // isGuest
            ));
        });

        // Waitlist mode
        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (currentMode != Mode.WAITLIST || newEntries == null) return;

            allEntries.clear();
            allEntries.addAll(newEntries);
            selectedEventWaitlistSize = allEntries.size();
            tvCount.setText("Total Entrants: " + selectedEventWaitlistSize);

            long countInvitedAccepted = allEntries.stream()
                    .filter(entry -> WaitlistEntry.STATUS_INVITED.equals(entry.getStatus())
                            || WaitlistEntry.STATUS_ACCEPTED.equals(entry.getStatus()))
                    .count();
            long countWaitlisted = allEntries.stream()
                    .filter(entry -> WaitlistEntry.STATUS_WAITLISTED.equals(entry.getStatus()))
                    .count();

            if (selectedEvent != null && selectedEvent.getEventCapacity() != null) {
                Log.d(TAG, "countInvitedAccepted: " + countInvitedAccepted);
                Log.d(TAG, "cap: " + selectedEvent.getEventCapacity());
                Log.d(TAG, "waitlist size: " + countWaitlisted);
                if (countInvitedAccepted < selectedEvent.getEventCapacity() && countWaitlisted > 0) {
                    btnDrawReplacement.setEnabled(true);
                } else {
                    btnDrawReplacement.setEnabled(false);
                }
            }

            applyFilter();
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

        viewModel.getEventDetails().observe(getViewLifecycleOwner(), event -> {
            if (event != null && currentMode == Mode.WAITLIST) {
                selectedEvent = event;
                updateExportButtonVisibility();
            }
        });
    }

    private void updateExportButtonVisibility() {
        if (selectedEvent == null) {
            btnExportCsv.setVisibility(View.GONE);
            return;
        }

        btnExportCsv.setVisibility(View.VISIBLE);
        Date now = new Date();
        boolean registrationEnded = selectedEvent.getRegistrationStart() != null && now.after(selectedEvent.getRegistrationEnd().toDate());
        
        btnExportCsv.setEnabled(registrationEnded);
        if (!registrationEnded) {
            btnExportCsv.setAlpha(0.5f);
            TooltipCompat.setTooltipText(btnExportCsv, "Available after registration ends");
        } else {
            btnExportCsv.setAlpha(1.0f);
            TooltipCompat.setTooltipText(btnExportCsv, null);
        }
    }

    private void showWaitlist(String eventTitle, String eventId) {
        currentMode = Mode.WAITLIST;
        tvEventName.setText(eventTitle);
        tvCount.setText("Loading...");
        btnDrawLottery.setVisibility(View.VISIBLE);
        filterContainer.setVisibility(View.VISIBLE);
        chipGroupFilter.check(R.id.chip_all);
        layoutCoOrganizers.setVisibility(View.VISIBLE);
        layoutSearchCoOrganizer.setVisibility(View.GONE);
        btnExportCsv.setVisibility(View.GONE);
        btnNotifyEntrants.setVisibility(View.VISIBLE);

        filteredEntries.clear();
        waitlistAdapter = new WaitlistAdapter(filteredEntries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
        viewModel.loadCoOrganizers(eventId);
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

    private void exportEnrolledListToCsv() {
        if (selectedEvent == null) return;

        List<WaitlistEntry> enrolled = allEntries.stream()
                .filter(e -> WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                .collect(Collectors.toList());

        if (enrolled.isEmpty()) {
            Toast.makeText(getContext(), "No enrolled entrants to export", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder csvData = new StringBuilder();
        csvData.append("Name,Email,Status,Registration Time\n");

        for (WaitlistEntry entry : enrolled) {
            csvData.append(entry.getUserName() != null ? entry.getUserName() : "Anonymous")
                    .append(",")
                    .append(entry.getUserEmail() != null ? entry.getUserEmail() : "N/A")
                    .append(",")
                    .append(entry.getStatus())
                    .append(",")
                    .append(new java.util.Date(entry.getTimestamp()).toString())
                    .append("\n");
        }

        try {
            String fileName = "enrolled_" + selectedEvent.getTitle().replaceAll("\\s+", "_") + ".csv";
            File file = new File(requireContext().getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csvData.toString().getBytes());
            fos.close();

            Uri contentUri = FileProvider.getUriForFile(requireContext(), "com.example.abacus_app.fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Enrolled Entrants: " + selectedEvent.getTitle());
            intent.putExtra(Intent.EXTRA_STREAM, contentUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            startActivity(Intent.createChooser(intent, "Export CSV"));

        } catch (IOException e) {
            Log.e(TAG, "Error exporting CSV", e);
            Toast.makeText(getContext(), "Failed to export CSV", Toast.LENGTH_SHORT).show();
        }
    }

    private void showNotifyCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notify_category, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_category_waitlist).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog(WaitlistEntry.STATUS_WAITLISTED);
        });

        dialogView.findViewById(R.id.btn_category_selected).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog(WaitlistEntry.STATUS_INVITED);
        });

        dialogView.findViewById(R.id.btn_category_cancelled).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectEntrantsDialog(WaitlistEntry.STATUS_CANCELLED);
        });

        dialog.show();
    }

    private void showSelectEntrantsDialog(String status) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_entrants, null);
        RecyclerView rvSelection = dialogView.findViewById(R.id.rv_entrants_selection);
        CheckBox cbSelectAll = dialogView.findViewById(R.id.cb_select_all);
        TextView tvTitle = dialogView.findViewById(R.id.tv_selection_title);

        String displayStatus = status.equals(WaitlistEntry.STATUS_INVITED) ? "Selected / Accepted" :
                status.equals(WaitlistEntry.STATUS_CANCELLED) ? "Cancelled / Declined" : "Waitlisted";
        tvTitle.setText("Select " + displayStatus + " Recipients");

        List<WaitlistEntry> targets;
        if (status.equals(WaitlistEntry.STATUS_CANCELLED)) {
            targets = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus()) || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else if (status.equals(WaitlistEntry.STATUS_INVITED)) {
            targets = allEntries.stream()
                    .filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus()) || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus()))
                    .collect(Collectors.toList());
        } else {
            targets = allEntries.stream()
                    .filter(e -> status.equals(e.getStatus()))
                    .collect(Collectors.toList());
        }

        if (targets.isEmpty()) {
            Toast.makeText(getContext(), "No users found in this category", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<String> selectedUserIds = new HashSet<>();
        EntrantSelectionAdapter adapter = new EntrantSelectionAdapter(targets, selectedUserIds);
        rvSelection.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSelection.setAdapter(adapter);

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                for (WaitlistEntry e : targets) selectedUserIds.add(e.getUserId());
            } else {
                selectedUserIds.clear();
            }
            adapter.notifyDataSetChanged();
        });

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Next", (dialog, which) -> {
                    if (selectedUserIds.isEmpty()) {
                        Toast.makeText(getContext(), "Please select at least one recipient", Toast.LENGTH_SHORT).show();
                    } else {
                        showComposeMessageDialog(new ArrayList<>(selectedUserIds));
                    }
                })
                .setNegativeButton("Back", (dialog, which) -> showNotifyCategoryDialog())
                .show();
    }

    private void showComposeMessageDialog(List<String> userIds) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_compose_message, null);
        TextView tvRecipientCount = dialogView.findViewById(R.id.tv_recipient_count);
        EditText etMessage = dialogView.findViewById(R.id.et_message);

        tvRecipientCount.setText("Sending to " + userIds.size() + " recipient(s)");

        new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setPositiveButton("Send", (dialog, which) -> {
                    String message = etMessage.getText().toString().trim();
                    if (message.isEmpty()) {
                        Toast.makeText(getContext(), "Message cannot be empty", Toast.LENGTH_SHORT).show();
                    } else {
                        viewModel.sendManualNotifications(selectedEventId, userIds, message);
                        Toast.makeText(getContext(), "Notifications sent successfully", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Back", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private class EntrantSelectionAdapter extends RecyclerView.Adapter<EntrantSelectionAdapter.ViewHolder> {
        private final List<WaitlistEntry> entrants;
        private final Set<String> selectedIds;

        public EntrantSelectionAdapter(List<WaitlistEntry> entrants, Set<String> selectedIds) {
            this.entrants = entrants;
            this.selectedIds = selectedIds;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entrant_selection, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            WaitlistEntry entry = entrants.get(position);
            holder.tvName.setText(entry.getUserName());
            holder.tvEmail.setText(entry.getUserEmail());
            holder.cbSelected.setChecked(selectedIds.contains(entry.getUserId()));

            holder.itemView.setOnClickListener(v -> {
                if (selectedIds.contains(entry.getUserId())) {
                    selectedIds.remove(entry.getUserId());
                } else {
                    selectedIds.add(entry.getUserId());
                }
                notifyItemChanged(position);
            });

            holder.cbSelected.setOnClickListener(v -> {
                if (selectedIds.contains(entry.getUserId())) {
                    selectedIds.remove(entry.getUserId());
                } else {
                    selectedIds.add(entry.getUserId());
                }
                notifyItemChanged(position);
            });
        }

        @Override
        public int getItemCount() {
            return entrants.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelected;
            TextView tvName, tvEmail;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                cbSelected = itemView.findViewById(R.id.cb_entrant_selected);
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
