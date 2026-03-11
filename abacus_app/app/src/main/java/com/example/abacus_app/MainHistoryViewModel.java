package com.example.abacus_app;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Architecture Layer: ViewModel
 *
 * Fetches and holds the current user's registration history from Firestore.
 * Maps raw status strings to human-readable display labels before exposing
 * them to the fragment.
 *
 * Used by: MainHistoryFragment
 */
public class MainHistoryViewModel extends ViewModel {

    private static final String TAG = "MainHistoryViewModel";
    private final RegistrationRepository registrationRepository;

    // State
    private final MutableLiveData<List<RegistrationHistoryItem>> registrations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");

    public MainHistoryViewModel(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * Loads the user's registration history from the repository.
     */
    public void loadRegistrationHistory() {
        Log.d(TAG, "Loading registration history...");
        isLoading.setValue(true);
        errorMessage.setValue("");

        registrationRepository.getHistoryForUser((registrationList, error) -> {
            Log.d(TAG, "Registration history callback received");
            isLoading.postValue(false);
            if (error == null && registrationList != null) {
                Log.d(TAG, "Successfully loaded " + registrationList.size() + " registrations");
                List<RegistrationHistoryItem> historyItems = new ArrayList<>();
                for (Registration registration : registrationList) {
                    historyItems.add(new RegistrationHistoryItem(
                            registration.getEventTitle(),
                            mapStatusToDisplayLabel(registration.getStatus()),
                            registration.getRegisteredAt()
                    ));
                }
                registrations.postValue(historyItems);
            } else {
                String errorMsg = error != null ? error.getMessage() : "Failed to load registration history";
                Log.e(TAG, "Failed to load registration history: " + errorMsg);
                errorMessage.postValue(errorMsg);
                registrations.postValue(new ArrayList<>()); // Show empty state
            }
        });
    }

    /**
     * Refreshes the registration history.
     */
    public void refresh() {
        Log.d(TAG, "Refreshing registration history...");
        loadRegistrationHistory();
    }


    public LiveData<List<RegistrationHistoryItem>> getRegistrations() {
        return registrations;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }


    /**
     * Maps raw status strings to human-readable display labels as per specification.
     */
    private String mapStatusToDisplayLabel(String status) {
        if (status == null) return "Unknown";
        
        switch (status.toLowerCase()) {
            case "waitlisted":
                return "On Waitlist";
            case "selected":
                return "Selected!";
            case "accepted":
                return "Enrolled";
            case "declined":
                return "Declined";
            case "cancelled":
                return "Cancelled";
            default:
                return status; // Return original if not recognized
        }
    }

    /**
     * Represents a single registration history item for display.
     */
    public static class RegistrationHistoryItem {
        private final String eventTitle;
        private final String statusLabel;
        private final long timestamp;

        public RegistrationHistoryItem(String eventTitle, String statusLabel, long timestamp) {
            this.eventTitle = eventTitle;
            this.statusLabel = statusLabel;
            this.timestamp = timestamp;
        }

        public String getEventTitle() { return eventTitle; }
        public String getStatusLabel() { return statusLabel; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Interface for RegistrationRepository that our ViewModel expects.
     * 
     * Currently bridged to Kaylee's RegistrationRepository via RegistrationRepositoryAdapter
     * in MainHistoryFragment. Once Kaylee's interface matches this exactly, we can use
     * her repository directly.
     */
    public interface RegistrationRepository {
        void getHistoryForUser(HistoryCallback callback);

        interface HistoryCallback {
            void onResult(List<Registration> registrations, Exception error);
        }
    }

    /**
     * Registration data model for our ViewModel.
     * 
     * Currently using our own model, but this should eventually be replaced
     * with Kaylee's Registration model or WaitlistEntry model when her schema
     * is finalized.
     */
    public static class Registration {
        private String eventTitle;
        private String status;
        private long registeredAt;

        public Registration() {} // Firestore requires empty constructor

        public Registration(String eventTitle, String status, long registeredAt) {
            this.eventTitle = eventTitle;
            this.status = status;
            this.registeredAt = registeredAt;
        }

        public String getEventTitle() { return eventTitle; }
        public String getStatus() { return status; }
        public long getRegisteredAt() { return registeredAt; }

        public void setEventTitle(String eventTitle) { this.eventTitle = eventTitle; }
        public void setStatus(String status) { this.status = status; }
        public void setRegisteredAt(long registeredAt) { this.registeredAt = registeredAt; }
    }
}