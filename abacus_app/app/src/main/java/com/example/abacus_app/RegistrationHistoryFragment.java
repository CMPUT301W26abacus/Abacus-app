package com.example.abacus_app;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

        try {
            // Initialize ViewModel with Firebase-backed repository
            FirebaseRegistrationRepository repositoryAdapter = new FirebaseRegistrationRepository(requireContext());
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
        } catch (Exception e) {
            Log.e("MainHistoryFragment", "Error initializing fragment", e);
            // Show error message to user
            Toast.makeText(requireContext(), "Error loading history: " + e.getMessage(), Toast.LENGTH_LONG).show();
            
            // Bind UI components even if there's an error
            bindViews(root);
            showEmptyState(true);
        }

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        try {
            // Load registration history when view is ready
            if (viewModel != null) {
                viewModel.loadRegistrationHistory();
            } else {
                Log.e("MainHistoryFragment", "ViewModel is null, cannot load history");
                showEmptyState(true);
            }
        } catch (Exception e) {
            Log.e("MainHistoryFragment", "Error loading registration history", e);
            Toast.makeText(requireContext(), "Error loading history data", Toast.LENGTH_SHORT).show();
            showEmptyState(true);
        }
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
        if (swipeRefreshLayout != null && viewModel != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                viewModel.refresh();
            });
        }
    }

    /**
     * Observes the ViewModel for changes in registration history, loading state, and error messages.
     */
    private void observeViewModel() {
        if (viewModel == null) {
            Log.e("MainHistoryFragment", "ViewModel is null, cannot observe");
            return;
        }

        // Observe registration list
        viewModel.getRegistrations().observe(getViewLifecycleOwner(), registrations -> {
            if (adapter != null) {
                adapter.updateRegistrations(registrations);
            }
            showEmptyState(registrations.isEmpty());
        });

        // Observe loading state
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            if (progressBar != null) {
                progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            }
            // Stop swipe refresh animation if loading is complete
            if (!isLoading && swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // Observe error messages
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                // Stop swipe refresh on error
                if (swipeRefreshLayout != null && swipeRefreshLayout.isRefreshing()) {
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
        if (recyclerView != null) {
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }    /**
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
                    .inflate(R.layout.item_event, parent, false);
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

            /**
             * Binds a RegistrationHistoryItem to the views in the ViewHolder, setting text, colors, and images
             * @param item The RegistrationHistoryItem containing the data to display in this ViewHolder
             */
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

            /**
             *  Maps a status label to a corresponding color resource.      
             * 
             * @param statusLabel The status label to map (e.g. "Selected!", "Enrolled", "On Waitlist", "Declined", "Cancelled").
             * @return The color resource ID corresponding to the status label.
             */
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

        /**
         * Creates a new instance of the given ViewModel class, injecting the RegistrationRepository dependency.
         *
         * @param modelClass The class of the ViewModel to create.
         * @param <T>        The type of the ViewModel.
         * @return A new instance of the specified ViewModel class.
         * @throws IllegalArgumentException if the modelClass is not assignable from MainHistoryViewModel.
         */
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
     * Firebase-backed implementation that fetches real user registration history.
     */
    private static class FirebaseRegistrationRepository implements MainHistoryViewModel.RegistrationRepository {
        private static final String TAG = "FirebaseRegRepo";
        private final Context context;
        private final RegistrationRemoteDataSource dataSource;
        private final FirebaseFirestore firestore;
        private final ExecutorService executor;

        public FirebaseRegistrationRepository(Context context) {
            this.context = context;
            this.dataSource = new RegistrationRemoteDataSource();
            this.firestore = FirebaseFirestore.getInstance();
            this.executor = Executors.newSingleThreadExecutor();
        }

        /**
         * Fetches the registration history for the current user by first retrieving the user's ID from the UserRepository, then querying the RegistrationRemoteDataSource for all waitlist entries associated with that user ID, and finally converting those waitlist entries into a list of Registration objects that can be displayed in the UI. This method runs asynchronously and returns the results through a callback to avoid blocking the main thread during Firebase queries.
         * 
         * @param callback Callback to receive the list of registrations or an error if one occurs.
         */
        @Override
        public void getHistoryForUser(HistoryCallback callback) {
            // Get current user ID from UserRepository
            UserLocalDataSource localDataSource = new UserLocalDataSource(context);
            UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(firestore);
            UserRepository userRepository = new UserRepository(localDataSource, remoteDataSource);

            userRepository.getCurrentUserId(userId -> {
                if (userId == null) {
                    Log.e(TAG, "No current user ID found");
                    callback.onResult(new ArrayList<>(), new Exception("User not authenticated"));
                    return;
                }

                // Execute Firebase query on background thread
                executor.execute(() -> {
                    try {
                        // Get user's waitlist entries across all events
                        ArrayList<WaitlistEntry> waitlistEntries = dataSource.getHistoryForUserSync(userId);
                        
                        if (waitlistEntries.isEmpty()) {
                            Log.d(TAG, "No registration history found for user: " + userId);
                            callback.onResult(new ArrayList<>(), null);
                            return;
                        }

                        Log.d(TAG, "Found " + waitlistEntries.size() + " registration entries");
                        
                        // Convert WaitlistEntry objects to Registration objects
                        List<MainHistoryViewModel.Registration> registrations = new ArrayList<>();
                        
                        // We'll fetch event titles in batches
                        List<String> eventIds = new ArrayList<>();
                        for (WaitlistEntry entry : waitlistEntries) {
                            eventIds.add(entry.getEventID());
                        }
                        
                        // Fetch event details to get titles
                        fetchEventTitles(eventIds, eventTitles -> {
                            for (WaitlistEntry entry : waitlistEntries) {
                                String eventTitle = eventTitles.get(entry.getEventID());
                                if (eventTitle == null) {
                                    eventTitle = "Event " + entry.getEventID(); // Fallback
                                }
                                
                                MainHistoryViewModel.Registration registration = new MainHistoryViewModel.Registration(
                                        eventTitle,
                                        entry.getStatus(),
                                        entry.getJoinTime() != null ? entry.getJoinTime().toDate().getTime() : System.currentTimeMillis()
                                );
                                registrations.add(registration);
                            }
                            
                            Log.d(TAG, "Successfully converted " + registrations.size() + " registrations");
                            callback.onResult(registrations, null);
                        });

                    } catch (Exception e) {
                        Log.e(TAG, "Failed to fetch user registration history", e);
                        callback.onResult(new ArrayList<>(), e);
                    }
                });
            });
        }

        /**
         * Fetch event titles for the given event IDs 
         * 
         * @param eventIds List of event IDs to fetch titles for
         * @param callback Callback to receive a map of event IDs to titles once fetching is complete
         * 
         */
        private void fetchEventTitles(List<String> eventIds, EventTitlesCallback callback) {
            java.util.Map<String, String> eventTitles = new java.util.HashMap<>();
            
            if (eventIds.isEmpty()) {
                callback.onResult(eventTitles);
                return;
            }

            // Counter to track async operations
            final int[] pendingRequests = {eventIds.size()};
            
            for (String eventId : eventIds) {
                firestore.collection("events")
                        .document(eventId)
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String title = documentSnapshot.getString("title");
                                if (title != null) {
                                    eventTitles.put(eventId, title);
                                }
                            }
                            
                            pendingRequests[0]--;
                            if (pendingRequests[0] == 0) {
                                callback.onResult(eventTitles);
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.w(TAG, "Failed to fetch title for event: " + eventId, e);
                            pendingRequests[0]--;
                            if (pendingRequests[0] == 0) {
                                callback.onResult(eventTitles);
                            }
                        });
            }
        }

        /**
         * Callback interface for receiving event titles after fetching from Firestore.
         */
        private interface EventTitlesCallback {
            void onResult(java.util.Map<String, String> eventTitles);
        }
    }
}