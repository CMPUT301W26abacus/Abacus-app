package com.example.abacus_app;

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
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Architecture Layer: View (Fragment)
 *
 * Displays a scrollable list of the current user's past event registrations
 * and their lottery outcomes. Shows an empty state when no registrations exist.
 *
 * Used by: MainActivity navigation
 */
public class MainHistoryFragment extends Fragment {

    private MainHistoryViewModel viewModel;
    private HistoryAdapter adapter;

    // UI Components
    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.main_history_fragment, container, false);

        // Initialize ViewModel with placeholder repository
        // TODO: Replace with actual RegistrationRepository when implemented
        PlaceholderRegistrationRepository repository = new PlaceholderRegistrationRepository();
        MainHistoryViewModelFactory factory = new MainHistoryViewModelFactory(repository);
        viewModel = new ViewModelProvider(this, factory).get(MainHistoryViewModel.class);

        // Bind UI components
        bindViews(root);

        // Set up RecyclerView
        setupRecyclerView();

        // Set up SwipeRefreshLayout
        setupSwipeRefresh();

        // Set up observers
        observeViewModel();

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load registration history when view is ready
        viewModel.loadRegistrationHistory();
    }

    private void bindViews(View root) {
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh);
        recyclerView = root.findViewById(R.id.rv_registrations);
        emptyStateLayout = root.findViewById(R.id.layout_empty_state);
        progressBar = root.findViewById(R.id.progress_bar);
    }

    private void setupRecyclerView() {
        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refresh();
        });
    }

    private void observeViewModel() {
        // Observe registration list
        viewModel.getRegistrations().observe(getViewLifecycleOwner(), registrations -> {
            adapter.updateRegistrations(registrations);
            showEmptyState(registrations.isEmpty());
        });

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            // Stop swipe refresh animation if loading is complete
            if (!isLoading && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                // Stop swipe refresh on error
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });
    }

    private void showEmptyState(boolean isEmpty) {
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /**
     * Adapter for displaying registration history items.
     */
    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<MainHistoryViewModel.RegistrationHistoryItem> registrations;

        public void updateRegistrations(List<MainHistoryViewModel.RegistrationHistoryItem> registrations) {
            this.registrations = registrations;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (registrations != null && position < registrations.size()) {
                MainHistoryViewModel.RegistrationHistoryItem item = registrations.get(position);
                holder.bind(item);
            }
        }

        @Override
        public int getItemCount() {
            return registrations != null ? registrations.size() : 0;
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView eventTitleView;
            private final TextView eventDateTimeView;
            private final TextView eventLocationView;
            private final TextView joinStatusButton;
            private final ImageView eventPosterView;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                eventTitleView = itemView.findViewById(R.id.tv_event_title);
                eventDateTimeView = itemView.findViewById(R.id.tv_event_datetime);
                eventLocationView = itemView.findViewById(R.id.tv_event_location);
                joinStatusButton = itemView.findViewById(R.id.btn_join_status);
                eventPosterView = itemView.findViewById(R.id.iv_event_poster);
            }

            void bind(MainHistoryViewModel.RegistrationHistoryItem item) {
                // Set event title
                eventTitleView.setText(item.getEventTitle());

                // Use datetime field to show status
                eventDateTimeView.setText(item.getStatusLabel());

                // Use location field to show registration date
                String formattedDate = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        .format(new Date(item.getTimestamp()));
                eventLocationView.setText("Registered on " + formattedDate);

                // Set status in the button and make it non-clickable (just for display)
                joinStatusButton.setText(item.getStatusLabel());
                joinStatusButton.setEnabled(false);

                // Set status button color based on status
                int statusColor = getStatusColor(item.getStatusLabel());
                joinStatusButton.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(itemView.getContext(), statusColor)));

                // Set a history icon for the poster
                eventPosterView.setImageResource(R.drawable.ic_history);
                eventPosterView.setScaleType(ImageView.ScaleType.CENTER);
            }

            private int getStatusColor(String statusLabel) {
                switch (statusLabel) {
                    case "Selected!":
                        return android.R.color.holo_green_light;
                    case "Enrolled":
                        return android.R.color.holo_blue_bright;
                    case "On Waitlist":
                        return android.R.color.holo_orange_light;
                    case "Declined":
                    case "Cancelled":
                        return android.R.color.holo_red_light;
                    default:
                        return android.R.color.darker_gray;
                }
            }
        }
    }

    /**
     * Factory for creating MainHistoryViewModel with dependencies.
     */
    private static class MainHistoryViewModelFactory implements ViewModelProvider.Factory {
        private final MainHistoryViewModel.RegistrationRepository repository;

        MainHistoryViewModelFactory(MainHistoryViewModel.RegistrationRepository repository) {
            this.repository = repository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(MainHistoryViewModel.class)) {
                return (T) new MainHistoryViewModel(repository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }

    // ------------------------------------------------------------------ //
    // Placeholder Repository Implementation
    // ------------------------------------------------------------------ //

    /**
     * Placeholder implementation until actual RegistrationRepository is created.
     */
    private static class PlaceholderRegistrationRepository implements MainHistoryViewModel.RegistrationRepository {
        @Override
        public void getHistoryForUser(HistoryCallback callback) {
            // Simulate network delay and return sample data
            new android.os.Handler().postDelayed(() -> {
                try {
                    // Sample data for demonstration
                    java.util.List<MainHistoryViewModel.Registration> sampleData = java.util.Arrays.asList(
                            new MainHistoryViewModel.Registration("Summer Music Festival", "selected",
                                    System.currentTimeMillis() - 86400000),
                            new MainHistoryViewModel.Registration("Art Gallery Opening", "waitlisted",
                                    System.currentTimeMillis() - 172800000),
                            new MainHistoryViewModel.Registration("Tech Meetup 2025", "accepted",
                                    System.currentTimeMillis() - 259200000),
                            new MainHistoryViewModel.Registration("Food Festival Downtown", "declined",
                                    System.currentTimeMillis() - 345600000),
                            new MainHistoryViewModel.Registration("Winter Sports Event", "cancelled",
                                    System.currentTimeMillis() - 432000000));
                    callback.onResult(sampleData, null);
                } catch (Exception e) {
                    callback.onResult(null, e);
                }
            }, 1000);
        }
    }
}