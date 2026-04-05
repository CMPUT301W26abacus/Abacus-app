package com.example.abacus_app;

import android.app.AlertDialog;
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
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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
 * Handles notifying entrants in different categories.
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
    private MaterialButton btnExportCsv, btnNotifyEntrants;

    private List<WaitlistEntry> allEntries = new ArrayList<>();
    private List<WaitlistEntry> filteredEntries = new ArrayList<>();
    
    // Co-organizer UI
    private LinearLayout layoutCoOrganizers;
    private MaterialButton btnAddCoOrganizer;
    private LinearLayout layoutSearchCoOrganizer;
    private TextInputEditText etSearchEntrant;
    private RecyclerView rvSearchResults, rvCoOrganizers;
    private UserSearchAdapter searchAdapter;
    private CoOrganizerAdapter coOrganizerAdapter;

    private List<User> searchResultsList = new ArrayList<>();
    private List<User> coOrganizersList = new ArrayList<>();

    private enum Mode { EVENT_LIST, WAITLIST }
    private Mode currentMode = Mode.EVENT_LIST;
    private String selectedEventId;
    private String selectedEventTitle;
    private Event selectedEvent;
    private boolean isDirectAccess = false; 

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            selectedEventId = getArguments().getString("EVENT_ID");
            selectedEventTitle = getArguments().getString("EVENT_TITLE");
        }
    }

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

        setupAdapters();
        setupListeners(view);
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
                        } else {
                            if (getActivity() instanceof MainActivity) {
                                ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
                            }
                        }
                    }
                });

        if (selectedEventId != null) {
            isDirectAccess = true;
            showWaitlist(selectedEventTitle, selectedEventId);
        } else {
            isDirectAccess = false;
            showEventList();
        }

        return view;
    }

    private void setupAdapters() {
        searchAdapter = new UserSearchAdapter(searchResultsList, user -> {
            if (selectedEventId != null) {
                viewModel.sendCoOrganizerInvite(selectedEventId, tvEventName.getText().toString(), user);
                Toast.makeText(getContext(), "Invitation sent to " + user.getName(), Toast.LENGTH_SHORT).show();
                etSearchEntrant.setText("");
                layoutSearchCoOrganizer.setVisibility(View.GONE);
                rvSearchResults.setVisibility(View.GONE);
            }
        });
        rvSearchResults.setAdapter(searchAdapter);

        coOrganizerAdapter = new CoOrganizerAdapter(coOrganizersList);
        rvCoOrganizers.setAdapter(coOrganizerAdapter);
    }

    private void setupListeners(View view) {
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST) {
                if (isDirectAccess) {
                    if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showFragment(R.id.nav_saved, true);
                } else {
                    showEventList();
                }
            } else {
                if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
            }
        });

        btnDrawLottery.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) viewModel.drawLottery(selectedEventId);
        });

        btnDrawReplacement.setOnClickListener(v -> {
            if (currentMode == Mode.WAITLIST && selectedEventId != null) viewModel.drawReplacement(selectedEventId);
        });

        btnExportCsv.setOnClickListener(v -> exportEnrolledListToCsv());
        btnNotifyEntrants.setOnClickListener(v -> showNotifyCategoryDialog());

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> applyFilter());

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
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 3) viewModel.searchUsersByEmail(query);
                else {
                    searchResultsList.clear();
                    searchAdapter.notifyDataSetChanged();
                    rvSearchResults.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
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
            String uuid = local.getUUIDSync();
            recyclerView.setAdapter(new EventAdapter(eventList, (title, autoJoin) -> {
                for (Event e : eventList) {
                    if (title.equals(e.getTitle())) {
                        selectedEventId = e.getEventId();
                        selectedEvent = e;
                        showWaitlist(e.getTitle(), selectedEventId);
                        break;
                    }
                }
            }, event -> {
                new AlertDialog.Builder(requireContext()).setTitle("Delete Event").setMessage("Are you sure?").setPositiveButton("Delete", (d, w) -> viewModel.deleteEvent(event.getEventId(), uuid)).setNegativeButton("Cancel", null).show();
            }, true, false, uuid, false));
        });

        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (currentMode != Mode.WAITLIST || newEntries == null) return;
            allEntries.clear();
            allEntries.addAll(newEntries);
            tvCount.setText("Total Entrants: " + allEntries.size());
            updateReplacementButtonState();
            applyFilter();
        });

        viewModel.getEventDetails().observe(getViewLifecycleOwner(), event -> {
            if (event != null && currentMode == Mode.WAITLIST) {
                selectedEvent = event;
                updateExportButtonVisibility();
            }
        });

        viewModel.getLotteryCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (currentMode == Mode.WAITLIST) {
                if (completed != null && completed) showDrawReplacementButton();
                else if (completed != null && !completed) showDrawLotteryButton();
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            if (currentMode == Mode.WAITLIST) {
                if (btnDrawLottery.getVisibility() == View.VISIBLE) {
                    btnDrawLottery.setEnabled(!loading);
                    btnDrawLottery.setText(loading ? "Processing..." : "Draw Lottery");
                } else if (btnDrawReplacement.getVisibility() == View.VISIBLE) {
                    btnDrawReplacement.setText(loading ? "Processing..." : "Draw Replacement");
                }
            }
        });

        viewModel.getSearchResults().observe(getViewLifecycleOwner(), users -> {
            searchResultsList.clear();
            if (users != null) searchResultsList.addAll(users);
            searchAdapter.notifyDataSetChanged();
            rvSearchResults.setVisibility(searchResultsList.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.getCoOrganizers().observe(getViewLifecycleOwner(), users -> {
            coOrganizersList.clear();
            if (users != null) coOrganizersList.addAll(users);
            coOrganizerAdapter.notifyDataSetChanged();
            layoutCoOrganizers.setVisibility(coOrganizersList.isEmpty() ? View.GONE : View.VISIBLE);
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });
    }

    private void updateReplacementButtonState() {
        if (selectedEvent != null && selectedEvent.getEventCapacity() != null) {
            long countActive = allEntries.stream().filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus()) || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus())).count();
            long countWaiting = allEntries.stream().filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus())).count();
            btnDrawReplacement.setEnabled(countActive < selectedEvent.getEventCapacity() && countWaiting > 0);
        }
    }

    private void showEventList() {
        currentMode = Mode.EVENT_LIST;
        selectedEventId = null;
        selectedEvent = null;
        tvEventName.setText("My Events");
        tvCount.setText("");
        btnDrawLottery.setVisibility(View.GONE);
        filterContainer.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.GONE);
        layoutCoOrganizers.setVisibility(View.GONE);
        btnExportCsv.setVisibility(View.GONE);
        btnNotifyEntrants.setVisibility(View.GONE);
        com.google.firebase.auth.FirebaseUser fbUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        if (fbUser != null) viewModel.loadOrganizerEvents(fbUser.getUid());
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
        btnNotifyEntrants.setVisibility(View.GONE);
        allEntries.clear();
        filteredEntries.clear();
        waitlistAdapter = new WaitlistAdapter(filteredEntries);
        recyclerView.setAdapter(waitlistAdapter);
        viewModel.loadWaitlist(eventId);
        viewModel.loadLotteryStatus(eventId);
        viewModel.loadCoOrganizers(eventId);
    }

    private void updateExportButtonVisibility() {
        if (selectedEvent == null) {
            btnExportCsv.setVisibility(View.GONE);
            btnNotifyEntrants.setVisibility(View.GONE);
            return;
        }
        btnExportCsv.setVisibility(View.VISIBLE);
        btnNotifyEntrants.setVisibility(View.VISIBLE);
        Date now = new Date();
        boolean ended = selectedEvent.getRegistrationEnd() != null && now.after(selectedEvent.getRegistrationEnd().toDate());
        btnExportCsv.setEnabled(ended);
        btnExportCsv.setAlpha(ended ? 1.0f : 0.5f);
        TooltipCompat.setTooltipText(btnExportCsv, ended ? null : "Available after registration ends");
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

    // ── Notify Entrants Flow ──────────────────────────────────────────────────

    private void showNotifyCategoryDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_notify_category, null);
        AlertDialog dialog = new AlertDialog.Builder(requireContext()).setView(dialogView).create();
        dialogView.findViewById(R.id.btn_category_waitlist).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectionDialog("Waitlisted", allEntries.stream().filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus())).collect(Collectors.toList()));
        });
        dialogView.findViewById(R.id.btn_category_selected).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectionDialog("Selected", allEntries.stream().filter(e -> WaitlistEntry.STATUS_INVITED.equals(e.getStatus()) || WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus())).collect(Collectors.toList()));
        });
        dialogView.findViewById(R.id.btn_category_cancelled).setOnClickListener(v -> {
            dialog.dismiss();
            showSelectionDialog("Cancelled/Declined", allEntries.stream().filter(e -> WaitlistEntry.STATUS_CANCELLED.equals(e.getStatus()) || WaitlistEntry.STATUS_DECLINED.equals(e.getStatus())).collect(Collectors.toList()));
        });
        dialog.show();
    }

    private void showSelectionDialog(String category, List<WaitlistEntry> list) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_select_entrants, null);
        ((TextView)view.findViewById(R.id.tv_selection_title)).setText("Select " + category + " recipients");
        CheckBox cbAll = view.findViewById(R.id.cb_select_all);
        RecyclerView rv = view.findViewById(R.id.rv_entrants_selection);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        Set<String> selectedIds = new HashSet<>();
        
        final EntrantSelectionAdapter[] adapterWrapper = new EntrantSelectionAdapter[1];
        adapterWrapper[0] = new EntrantSelectionAdapter(list, (entry, selected) -> {
            if (selected) selectedIds.add(entry.getUserID()); else selectedIds.remove(entry.getUserID());
            cbAll.setOnCheckedChangeListener(null);
            cbAll.setChecked(selectedIds.size() == list.size() && !list.isEmpty());
            cbAll.setOnCheckedChangeListener((b, isChecked) -> {
                if (isChecked) list.forEach(e -> selectedIds.add(e.getUserID())); else selectedIds.clear();
                adapterWrapper[0].updateSelection(selectedIds);
            });
        });
        rv.setAdapter(adapterWrapper[0]);
        cbAll.setOnCheckedChangeListener((b, isChecked) -> {
            if (isChecked) list.forEach(e -> selectedIds.add(e.getUserID())); else selectedIds.clear();
            adapterWrapper[0].updateSelection(selectedIds);
        });
        new AlertDialog.Builder(requireContext()).setView(view).setPositiveButton("Next", (d, w) -> {
            if (selectedIds.isEmpty()) Toast.makeText(getContext(), "None selected", Toast.LENGTH_SHORT).show();
            else showMessageDialog(new ArrayList<>(selectedIds));
        }).setNegativeButton("Back", (d, w) -> showNotifyCategoryDialog()).show();
    }

    private void showMessageDialog(List<String> userIds) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_compose_message, null);
        TextView tvCountLabel = view.findViewById(R.id.tv_recipient_count);
        tvCountLabel.setText("Sending to " + userIds.size() + " recipient" + (userIds.size() == 1 ? "" : "s"));
        
        TextInputEditText etMessage = view.findViewById(R.id.et_message);

        new AlertDialog.Builder(requireContext())
                .setView(view)
                .setPositiveButton("Send Now", (d, w) -> {
                    String msg = etMessage.getText().toString().trim();
                    if (msg.isEmpty()) {
                        Toast.makeText(getContext(), "Please type a message", Toast.LENGTH_SHORT).show();
                    } else {
                        viewModel.sendManualNotifications(selectedEventId, userIds, msg);
                        Toast.makeText(getContext(), "Notifications queued for sending", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Back", (d, w) -> showSelectionDialog("Recipients", new ArrayList<>())) // Simplified back logic
                .show();
    }

    private static class EntrantSelectionAdapter extends RecyclerView.Adapter<EntrantSelectionAdapter.VH> {
        private final List<WaitlistEntry> list;
        private final Set<String> selected = new HashSet<>();
        private final SelectionListener listener;
        interface SelectionListener { void onToggle(WaitlistEntry entry, boolean isSelected); }
        EntrantSelectionAdapter(List<WaitlistEntry> list, SelectionListener l) { this.list = list; this.listener = l; }
        void updateSelection(Set<String> s) { selected.clear(); selected.addAll(s); notifyDataSetChanged(); }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_entrant_selection, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int p) {
            WaitlistEntry e = list.get(p);
            h.tvN.setText(e.getUserName() != null ? e.getUserName() : "Anonymous");
            h.tvE.setText(e.getUserEmail() != null ? e.getUserEmail() : "No email");
            h.cb.setOnCheckedChangeListener(null);
            h.cb.setChecked(selected.contains(e.getUserID()));
            h.cb.setOnCheckedChangeListener((b, isChecked) -> {
                if (isChecked) selected.add(e.getUserID()); else selected.remove(e.getUserID());
                listener.onToggle(e, isChecked);
            });
        }
        @Override public int getItemCount() { return list.size(); }
        static class VH extends RecyclerView.ViewHolder {
            CheckBox cb; TextView tvN, tvE;
            VH(View v) { super(v); cb = v.findViewById(R.id.cb_entrant_selected); tvN = v.findViewById(R.id.tv_entrant_name); tvE = v.findViewById(R.id.tv_entrant_email); }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void exportEnrolledListToCsv() {
        if (selectedEvent == null) return;
        List<WaitlistEntry> enrolled = allEntries.stream().filter(e -> WaitlistEntry.STATUS_ACCEPTED.equals(e.getStatus())).collect(Collectors.toList());
        if (enrolled.isEmpty()) { Toast.makeText(getContext(), "No enrolled entrants to export", Toast.LENGTH_SHORT).show(); return; }
        StringBuilder csv = new StringBuilder("Name,Email,Status,Time\n");
        for (WaitlistEntry e : enrolled) csv.append(e.getUserName()).append(",").append(e.getUserEmail()).append(",").append(e.getStatus()).append(",").append(new Date(e.getTimestamp())).append("\n");
        try {
            File file = new File(requireContext().getCacheDir(), "enrolled_" + selectedEvent.getTitle().replaceAll("\\s+", "_") + ".csv");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(csv.toString().getBytes());
            fos.close();
            Uri uri = FileProvider.getUriForFile(requireContext(), "com.example.abacus_app.fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/csv");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export CSV"));
        } catch (IOException e) { Log.e(TAG, "CSV error", e); }
    }

    private void showDrawLotteryButton() {
        btnDrawLottery.setVisibility(View.VISIBLE);
        btnDrawReplacement.setVisibility(View.GONE);
    }

    private void showDrawReplacementButton() {
        btnDrawLottery.setVisibility(View.GONE);
        btnDrawReplacement.setVisibility(View.VISIBLE);
    }
}
