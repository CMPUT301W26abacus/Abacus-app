package com.example.abacus_app;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Architecture Layer: View (Fragment)
 *
 * Displays the user's event registration history with:
 *   - Consistent top header (AppBarLayout, same as all other pages)
 *   - Status tabs: All | Active (waitlisted/selected/enrolled) | Closed (declined/cancelled)
 *   - Date range picker (calendar icon in title bar)
 *   - Pull-to-refresh
 *
 * Guest support: reads the stored guest email from SharedPreferences when running in guest mode.
 */
public class RegistrationHistoryFragment extends Fragment {

    private RegistrationHistoryViewModel viewModel;
    private HistoryAdapter adapter;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;
    private TextView tvDateRangeLabel;

    private final Set<String> activeStatusFilter = new HashSet<>();
    private long[] activeDateRange = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.registration_history_fragment, container, false);

        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh);
        recyclerView       = root.findViewById(R.id.rv_registrations);
        emptyStateLayout   = root.findViewById(R.id.layout_empty_state);
        progressBar        = root.findViewById(R.id.progress_bar);
        tvDateRangeLabel   = root.findViewById(R.id.tv_date_range_label);

        setupRecyclerView();
        setupViewModel();
        setupSwipeRefresh();
        setupDateFilter(root);
        setupStatusFilter(root);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.loadRegistrationHistory();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Keep history in sync with join/leave actions done in EventDetailsFragment.
        if (viewModel != null) {
            viewModel.refresh();
        }
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter((eventId, eventTitle) -> {
            Bundle args = new Bundle();
            args.putString(EventDetailsFragment.ARG_EVENT_ID,    eventId);
            args.putString(EventDetailsFragment.ARG_EVENT_TITLE, eventTitle);
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_nav_history_to_eventDetailsFragment, args);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupViewModel() {
        FirebaseRegistrationRepository repository =
                new FirebaseRegistrationRepository(requireContext());
        viewModel = new ViewModelProvider(this,
        new RegistrationHistoryViewModel.Factory(repository))
        .get(RegistrationHistoryViewModel.class);

        viewModel.getRegistrations().observe(getViewLifecycleOwner(), registrations -> {
            adapter.setItems(registrations);
            applyCurrentFilter();
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (!loading) swipeRefreshLayout.setRefreshing(false);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.refresh());
    }

    private void setupStatusFilter(View root) {
        ImageButton btnStatusFilter = root.findViewById(R.id.btnStatusFilter);
        final String[] statusLabels = {"On Waitlist", "Selected!", "Enrolled", "Declined", "Cancelled"};
        final boolean[] checked = new boolean[statusLabels.length];

        btnStatusFilter.setOnClickListener(v -> {
            for (int i = 0; i < statusLabels.length; i++) {
                // empty filter = all showing, so pre-check everything
                checked[i] = activeStatusFilter.isEmpty()
                        || activeStatusFilter.contains(statusLabels[i]);
            }
            new AlertDialog.Builder(requireContext())
                    .setTitle("Filter by status")
                    .setMultiChoiceItems(statusLabels, checked,
                            (dialog, which, isChecked) -> checked[which] = isChecked)
                    .setPositiveButton("Apply", (dialog, which) -> {
                        activeStatusFilter.clear();
                        boolean allChecked = true;
                        for (int i = 0; i < statusLabels.length; i++) {
                            if (checked[i]) activeStatusFilter.add(statusLabels[i]);
                            else allChecked = false;
                        }
                        // all selected = same as no filter
                        if (allChecked) activeStatusFilter.clear();
                        applyCurrentFilter();
                        if (!activeStatusFilter.isEmpty()) {
                            btnStatusFilter.setColorFilter(
                                    ContextCompat.getColor(requireContext(), R.color.black));
                        } else {
                            btnStatusFilter.clearColorFilter();
                        }
                    })
                    .setNeutralButton("Clear all", (dialog, which) -> {
                        activeStatusFilter.clear();
                        Arrays.fill(checked, false);
                        applyCurrentFilter();
                        btnStatusFilter.clearColorFilter();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void setupDateFilter(View root) {
        ImageButton btnDateFilter = root.findViewById(R.id.btnDateFilter);
        btnDateFilter.setOnClickListener(v -> {
            MaterialDatePicker<Pair<Long, Long>> picker =
                    MaterialDatePicker.Builder.dateRangePicker()
                            .setTitleText("Filter by date range")
                            .build();
            picker.show(getParentFragmentManager(), "DATE_RANGE_PICKER");

            picker.addOnPositiveButtonClickListener(selection -> {
                if (selection != null && selection.first != null && selection.second != null) {
                    activeDateRange = new long[]{selection.first, selection.second};
                    applyCurrentFilter();
                    String fmt = "MMM d";
                    String label = new SimpleDateFormat(fmt, Locale.getDefault())
                            .format(new Date(selection.first))
                            + " – "
                            + new SimpleDateFormat(fmt, Locale.getDefault())
                            .format(new Date(selection.second));
                    tvDateRangeLabel.setText("Showing: " + label + "  ·  Tap calendar to change");
                    tvDateRangeLabel.setVisibility(View.VISIBLE);
                    // Tint icon to indicate active filter
                    btnDateFilter.setColorFilter(
                            ContextCompat.getColor(requireContext(), R.color.black));
                }
            });

            picker.addOnNegativeButtonClickListener(v2 -> {
                activeDateRange = null;
                applyCurrentFilter();
                tvDateRangeLabel.setVisibility(View.GONE);
                btnDateFilter.clearColorFilter();
            });
        });
    }

    private void applyCurrentFilter() {
        adapter.setFilter(activeStatusFilter, activeDateRange);
        boolean empty = adapter.getItemCount() == 0;
        emptyStateLayout.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);

        TextView tvTitle    = emptyStateLayout.findViewById(R.id.tv_empty_state_title);
        TextView tvSubtitle = emptyStateLayout.findViewById(R.id.tv_empty_state_subtitle);
        if (tvTitle != null && tvSubtitle != null) {
            if (activeDateRange != null && !activeStatusFilter.isEmpty()) {
                tvTitle.setText("No results");
                tvSubtitle.setText("No registrations match the selected filters and date range.");
            } else if (activeDateRange != null) {
                tvTitle.setText("No results in this date range");
                tvSubtitle.setText("Try expanding the range or clearing the filter.");
            } else if (!activeStatusFilter.isEmpty()) {
                tvTitle.setText("No matching registrations");
                tvSubtitle.setText("No registrations match the selected status filters.");
            } else {
                tvTitle.setText("No Registration History");
                tvSubtitle.setText("You haven't registered for any events yet.");
            }
        }
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    private static class HistoryAdapter
            extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {


        interface OnEventClickListener {
            void onEventClick(String eventId, String eventTitle);
        }

        private List<RegistrationHistoryViewModel.RegistrationHistoryItem> items =
                new ArrayList<>();
        private List<RegistrationHistoryViewModel.RegistrationHistoryItem> masterItems =
                new ArrayList<>();
        private final OnEventClickListener clickListener;

        HistoryAdapter(OnEventClickListener clickListener) {
            this.clickListener = clickListener;
        }

        void setItems(List<RegistrationHistoryViewModel.RegistrationHistoryItem> newItems) {
            masterItems = newItems;
            items = new ArrayList<>(newItems);
            notifyDataSetChanged();
        }

        /**
         * @param selectedStatuses set of status labels to show; empty means show all
         * @param dateRange        [startMs, endMs] or null
         */
        void setFilter(Set<String> selectedStatuses, long[] dateRange) {
            List<RegistrationHistoryViewModel.RegistrationHistoryItem> filtered =
                    new ArrayList<>();
            for (RegistrationHistoryViewModel.RegistrationHistoryItem item : masterItems) {
                boolean statusOk = selectedStatuses.isEmpty()
                        || selectedStatuses.contains(item.getStatusLabel());

                boolean dateOk = true;
                if (dateRange != null && dateRange.length == 2) {
                    long ts = item.getTimestamp();
                    dateOk = ts >= dateRange[0] && ts <= (dateRange[1] + 86_400_000L);
                }

                if (statusOk && dateOk) filtered.add(item);
            }
            items = filtered;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_event, parent, false);
            return new ViewHolder(view, clickListener);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position >= 0 && position < items.size()) {
                holder.bind(items.get(position));
            }
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView titleView, dateTimeView, locationView;
            private final com.google.android.material.button.MaterialButton statusButton;
            private final ImageView posterView;
            private final OnEventClickListener clickListener;

            ViewHolder(@NonNull View itemView, OnEventClickListener clickListener) {
                super(itemView);
                this.clickListener = clickListener;
                titleView    = itemView.findViewById(R.id.tv_event_title);
                dateTimeView = itemView.findViewById(R.id.tv_event_datetime);
                locationView = itemView.findViewById(R.id.tv_event_location);
                statusButton = itemView.findViewById(R.id.btn_join_status);
                posterView   = itemView.findViewById(R.id.iv_event_poster);
            }

            void bind(RegistrationHistoryViewModel.RegistrationHistoryItem item) {
                titleView.setText(item.getEventTitle());
                dateTimeView.setText(item.getStatusLabel());
                locationView.setText("Registered " + new SimpleDateFormat(
                        "MMM dd, yyyy", Locale.getDefault())
                        .format(new Date(item.getTimestamp())));

                statusButton.setText(item.getStatusLabel());
                statusButton.setEnabled(false);
                statusButton.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(
                                        itemView.getContext(),
                                        statusColor(item.getStatusLabel()))));

                String posterUrl = item.getPosterImageUrl();
                if (posterUrl != null && !posterUrl.isEmpty()) {
                    Glide.with(itemView.getContext())
                            .load(posterUrl)
                            .placeholder(R.drawable.ic_event_poster)
                            .error(R.drawable.ic_event_poster)
                            .centerCrop()
                            .into(posterView);
                } else {
                    posterView.setImageResource(R.drawable.ic_event_poster);
                    posterView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                }

                itemView.setOnClickListener(v -> {
                    if (clickListener != null)
                        clickListener.onEventClick(item.getEventId(), item.getEventTitle());
                });
            }

            private int statusColor(String status) {
                if (status == null) return android.R.color.darker_gray;
                switch (status) {
                    case "Selected!":    return android.R.color.holo_green_light;
                    case "Enrolled":     return android.R.color.holo_blue_bright;
                    case "On Waitlist":  return android.R.color.holo_orange_light;
                    case "Declined":
                    case "Not Selected":
                    case "Cancelled":    return android.R.color.holo_red_light;
                    default:             return android.R.color.darker_gray;
                }
            }
        }
    }

    // ─── Firebase Repository ──────────────────────────────────────────────────

    private static class FirebaseRegistrationRepository
            implements RegistrationHistoryViewModel.RegistrationRepository {

        private static final String TAG = "FirebaseRegRepo";

        private final Context context;
        private final RegistrationRemoteDataSource dataSource;
        private final FirebaseFirestore firestore;
        private final ExecutorService executor;

        FirebaseRegistrationRepository(Context context) {
            this.context    = context;
            this.dataSource = new RegistrationRemoteDataSource();
            this.firestore  = FirebaseFirestore.getInstance();
            this.executor   = Executors.newSingleThreadExecutor();
        }

        @Override
        public void getHistoryForUser(
        RegistrationHistoryViewModel.RegistrationRepository.HistoryCallback callback) {

            boolean isGuest = false;
            if (context instanceof MainActivity) {
                isGuest = ((MainActivity) context).isGuestSession();
            } else if (context instanceof android.app.Activity) {
                isGuest = ((android.app.Activity) context)
                        .getIntent().getBooleanExtra("isGuest", false);
            }

            if (isGuest) {
                String guestEmail = context
                        .getSharedPreferences(
                                GuestSignUpFragment.PREFS_GUEST, Context.MODE_PRIVATE)
                        .getString(GuestSignUpFragment.PREF_GUEST_EMAIL, null);

                if (guestEmail == null) {
                    callback.onResult(new ArrayList<>(), null);
                    return;
                }

                final String email = guestEmail;
                executor.execute(() -> {
                    try {
                        ArrayList<WaitlistEntry> entries =
                                dataSource.getHistoryForGuestSync(email);
                        if (entries == null || entries.isEmpty()) {
                            callback.onResult(new ArrayList<>(), null);
                            return;
                        }
                        buildRegistrationList(entries, callback);
                    } catch (Exception e) {
                        callback.onResult(new ArrayList<>(), e);
                    }
                });
                return;
            }

            FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
            String email = authUser != null ? authUser.getEmail() : null;
            final String emailKey = (email != null && !email.trim().isEmpty())
                    ? GuestSignUpFragment.emailToKey(email.trim().toLowerCase(Locale.US))
                    : null;

            UserLocalDataSource localDataSource = new UserLocalDataSource(context);
            final String uuidKey = localDataSource.getUUIDSync();

            final List<String> userKeys = new ArrayList<>();
            if (emailKey != null && !emailKey.trim().isEmpty()) {
                userKeys.add(emailKey);
            }
            if (uuidKey != null && !uuidKey.trim().isEmpty() && !uuidKey.equals(emailKey)) {
                userKeys.add(uuidKey);
            }

            if (userKeys.isEmpty()) {
                callback.onResult(new ArrayList<>(), null);
                return;
            }

            executor.execute(() -> {
                try {
                    Map<String, WaitlistEntry> mergedByEventId = new LinkedHashMap<>();
                    for (String key : userKeys) {
                        ArrayList<WaitlistEntry> entriesForKey = dataSource.getHistoryForUserSync(key);
                        if (entriesForKey == null) continue;

                        for (WaitlistEntry entry : entriesForKey) {
                            if (entry == null || entry.getEventId() == null) continue;
                            // One registration history record per event per user.
                            mergedByEventId.put(entry.getEventId(), entry);
                        }
                    }

                    if (mergedByEventId.isEmpty()) {
                        callback.onResult(new ArrayList<>(), null);
                        return;
                    }

                    ArrayList<WaitlistEntry> waitlistEntries =
                            new ArrayList<>(mergedByEventId.values());
                    buildRegistrationList(waitlistEntries, callback);
                } catch (Exception e) {
                    callback.onResult(new ArrayList<>(), e);
                }
            });
        }

        private void buildRegistrationList(
                ArrayList<WaitlistEntry> entries,
        RegistrationHistoryViewModel.RegistrationRepository.HistoryCallback callback) {

            List<String> eventIds = new ArrayList<>();
            for (WaitlistEntry entry : entries) {
                if (entry.getEventId() != null) eventIds.add(entry.getEventId());
            }

            fetchEventData(eventIds, (eventTitles, eventPosters) -> {
                List<RegistrationHistoryViewModel.Registration> registrations = new ArrayList<>();
                for (WaitlistEntry entry : entries) {
                    String title = eventTitles.containsKey(entry.getEventId())
                            ? eventTitles.get(entry.getEventId())
                            : "Event " + entry.getEventId();
                    String posterUrl = eventPosters.get(entry.getEventId());

            registrations.add(new RegistrationHistoryViewModel.Registration(
                            entry.getEventId(),
                            title,
                            posterUrl,
                            entry.getStatus(),
                            entry.getTimestamp() != null
                                    ? entry.getTimestamp()
                                    : System.currentTimeMillis()
                    ));
                }
                callback.onResult(registrations, null);
            });
        }

        private void fetchEventData(List<String> eventIds, EventDataCallback callback) {
            Map<String, String> eventTitles  = new HashMap<>();
            Map<String, String> eventPosters = new HashMap<>();
            if (eventIds.isEmpty()) {
                callback.onResult(eventTitles, eventPosters);
                return;
            }

            final int[] pending = {eventIds.size()};
            for (String eventId : eventIds) {
                firestore.collection("events").document(eventId).get()
                        .addOnSuccessListener(doc -> {
                            if (doc.exists()) {
                                String title = doc.getString("title");
                                if (title != null) eventTitles.put(eventId, title);
                                String posterUrl = doc.getString("posterImageUrl");
                                if (posterUrl != null) eventPosters.put(eventId, posterUrl);
                            }
                            if (--pending[0] == 0) callback.onResult(eventTitles, eventPosters);
                        })
                        .addOnFailureListener(e -> {
                            if (--pending[0] == 0) callback.onResult(eventTitles, eventPosters);
                        });
            }
        }

        private interface EventDataCallback {
            void onResult(Map<String, String> eventTitles, Map<String, String> eventPosters);
        }
    }
}
