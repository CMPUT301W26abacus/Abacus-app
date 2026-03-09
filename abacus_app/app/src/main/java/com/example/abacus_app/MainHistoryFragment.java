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

        // Initialize ViewModel with Kaylee's RegistrationRepository
        RegistrationRepositoryAdapter repositoryAdapter = new RegistrationRepositoryAdapter();
        MainHistoryViewModelFactory factory = new MainHistoryViewModelFactory(repositoryAdapter);
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

    /**
     * Binds UI components to their respective views.
     *
     * @param root
     */
    private void bindViews(View root) {
        swipeRefreshLayout = root.findViewById(R.id.swipe_refresh);
        recyclerView = root.findViewById(R.id.rv_registrations);
        emptyStateLayout = root.findViewById(R.id.layout_empty_state);
        progressBar = root.findViewById(R.id.progress_bar);
    }

    /**
     * Sets up the RecyclerView and its adapter.
     */
    private void setupRecyclerView() {
        adapter = new HistoryAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    /**
     * Sets up the SwipeRefreshLayout for refreshing the registration history.
     */
    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.refresh();
        });
    }

    /**
     * Observes the ViewModel for changes in registration history, loading state, and error messages.
     */
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
/**
     * Shows or hides the empty state layout based on whether the registration history is empty.
     *
     * @param isEmpty True if the registration history is empty, false otherwise.
     */
    private void showEmptyState(boolean isEmpty) {
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    /**
     * Adapter for displaying registration history items.
     */
    private static class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

        private List<MainHistoryViewModel.RegistrationHistoryItem> registrations;

        /**
         * Updates the adapter with a new list of registration history items.
         * @param registrations List of registration history items.
         */
        public void updateRegistrations(List<MainHistoryViewModel.RegistrationHistoryItem> registrations) {
            this.registrations = registrations;
            notifyDataSetChanged();
        }

        /**
         * Creates a new ViewHolder for the RecyclerView.
         * @param parent   The ViewGroup into which the new View will be added after it is bound to
         *                 an adapter position.
         * @param viewType The view type of the new View.
         * @return A new ViewHolder for the RecyclerView.
         */
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_list, parent, false);
            return new ViewHolder(view);
        }

        /**
         * Binds a registration history item to a ViewHolder.
         * @param holder   The ViewHolder to bind the item to.
         * @param position The position of the item in the list.
         */
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (registrations != null && position < registrations.size()) {
                MainHistoryViewModel.RegistrationHistoryItem item = registrations.get(position);
                holder.bind(item);
            }
        }

        /**
         * Returns the total number of items in the adapter.
         * @return Total number of items.
         */
        @Override
        public int getItemCount() {
            return registrations != null ? registrations.size() : 0;
        }

        /**
         * ViewHolder for displaying registration history items.
         */
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

    /**
     * Adapter class to bridge Kaylee's RegistrationRepository with our ViewModel interface.
     * This allows us to use Kaylee's concrete RegistrationRepository class with our
     * MainHistoryViewModel that expects a specific interface.
     * 
     * INTEGRATION STATUS: ✅ COMPLETE - Now using real getHistoryForUser implementation
     * 
     * TODO: 
     * - Replace "current_user_id" placeholder with actual user authentication
     * - Implement event title lookup using EventRepository or similar
     * - Consider migrating to Kaylee's models directly once interfaces align
     */
    private static class RegistrationRepositoryAdapter implements MainHistoryViewModel.RegistrationRepository {
        private final RegistrationRepository kayleeRepo;

        public RegistrationRepositoryAdapter() {
            // Initialize Kaylee's repository
            this.kayleeRepo = new RegistrationRepository();
        }

        @Override
        public void getHistoryForUser(MainHistoryViewModel.RegistrationRepository.HistoryCallback callback) {
            // Use the actual implemented method from Kaylee's repository
            // TODO: Get the current user ID from authentication/session management
            String currentUserId = "current_user_id"; // Placeholder - replace with actual user ID
            
            kayleeRepo.getHistoryForUser(currentUserId, waitlistEntries -> {
                try {
                    // Convert WaitlistEntry objects to Registration objects
                    java.util.List<MainHistoryViewModel.Registration> registrations = new java.util.ArrayList<>();
                    
                    for (WaitlistEntry entry : waitlistEntries) {
                        // TODO: Get actual event title using entry.getEventID()
                        String eventTitle = "Event " + entry.getEventID(); // Placeholder
                        
                        MainHistoryViewModel.Registration registration = new MainHistoryViewModel.Registration(
                            eventTitle,
                            entry.getStatus().toLowerCase(), // Convert to lowercase for consistency
                            entry.getJoinTime().toDate().getTime()
                        );
                        registrations.add(registration);
                    }
                    
                    callback.onResult(registrations, null);
                } catch (Exception e) {
                    callback.onResult(null, e);
                }
            });
        }
    }
}