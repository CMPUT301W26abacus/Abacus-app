package com.example.abacus_app;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Architecture Layer: View (Fragment)
 *
 * Displays a scrollable list of the current user's past event registrations
 * and their lottery outcomes. Shows an empty state when no registrations exist.
 *
 * Used by: MainActivity navigation
 */
public class RegistrationHistoryFragment extends Fragment {

    private RegistrationHistoryViewModel viewModel;
    private HistoryAdapter adapter;

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;

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

        setupRecyclerView();
        setupViewModel();
        setupSwipeRefresh();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel.loadRegistrationHistory();
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
        FirebaseRegistrationRepository repository = new FirebaseRegistrationRepository(requireContext());
        viewModel = new ViewModelProvider(this,
                new RegistrationHistoryViewModel.Factory(repository))
                .get(RegistrationHistoryViewModel.class);

        viewModel.getRegistrations().observe(getViewLifecycleOwner(), registrations -> {
            adapter.setItems(registrations);
            emptyStateLayout.setVisibility(registrations.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(registrations.isEmpty() ? View.GONE : View.VISIBLE);
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

    // ─── Adapter ─────────────────────────────────────────────────────────────────

    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        interface OnEventClickListener {
            void onEventClick(String eventId, String eventTitle);
        }

        private List<RegistrationHistoryViewModel.RegistrationHistoryItem> items = new ArrayList<>();
        private final OnEventClickListener clickListener;

        HistoryAdapter(OnEventClickListener clickListener) {
            this.clickListener = clickListener;
        }

        void setItems(List<RegistrationHistoryViewModel.RegistrationHistoryItem> newItems) {
            items = newItems;
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
        public int getItemCount() {
            return items.size();
        }

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
                locationView.setText("Registered on " + new SimpleDateFormat("MMM dd, yyyy",
                        Locale.getDefault()).format(new Date(item.getTimestamp())));

                statusButton.setText(item.getStatusLabel());
                statusButton.setEnabled(false);
                statusButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(itemView.getContext(), statusColor(item.getStatusLabel()))));

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
                switch (status) {
                    case "Selected!":   return android.R.color.holo_green_light;
                    case "Enrolled":    return android.R.color.holo_blue_bright;
                    case "On Waitlist": return android.R.color.holo_orange_light;
                    case "Declined":
                    case "Cancelled":   return android.R.color.holo_red_light;
                    default:            return android.R.color.darker_gray;
                }
            }
        }
    }

    // ─── Firebase Repository ──────────────────────────────────────────────────────

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

            UserLocalDataSource localDataSource   = new UserLocalDataSource(context);
            UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(firestore);
            UserRepository userRepository         = new UserRepository(localDataSource, remoteDataSource);

            userRepository.getCurrentUserId(userId -> {
                if (userId == null) {
                    callback.onResult(new ArrayList<>(), null);
                    return;
                }

                executor.execute(() -> {
                    try {
                        ArrayList<WaitlistEntry> waitlistEntries = dataSource.getHistoryForUserSync(userId);

                        if (waitlistEntries.isEmpty()) {
                            callback.onResult(new ArrayList<>(), null);
                            return;
                        }

                        List<String> eventIds = new ArrayList<>();
                        for (WaitlistEntry entry : waitlistEntries) {
                            eventIds.add(entry.getEventID());
                        }

                        fetchEventData(eventIds, (eventTitles, eventPosters) -> {
                            List<RegistrationHistoryViewModel.Registration> registrations = new ArrayList<>();
                            for (WaitlistEntry entry : waitlistEntries) {
                                String title = eventTitles.containsKey(entry.getEventID())
                                        ? eventTitles.get(entry.getEventID())
                                        : "Event " + entry.getEventID();
                                String posterUrl = eventPosters.get(entry.getEventID());

                                registrations.add(new RegistrationHistoryViewModel.Registration(
                                        entry.getEventID(),
                                        title,
                                        posterUrl,
                                        entry.getStatus(),
                                        entry.getJoinTime() != null
                                                ? entry.getJoinTime().toDate().getTime()
                                                : System.currentTimeMillis()
                                ));
                            }
                            callback.onResult(registrations, null);
                        });

                    } catch (Exception e) {
                        callback.onResult(new ArrayList<>(), e);
                    }
                });
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