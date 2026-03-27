package com.example.abacus_app;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * ViewModel for the registration history screen.
 *
 * Fetches and holds the current user's registration history,
 * mapping raw Firestore status strings to human-readable labels.
 *
 * Covers: US 01.02.03 — View Registration History
 */
public class MainHistoryViewModel extends ViewModel {

    private final MutableLiveData<List<RegistrationHistoryItem>> registrations =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading    = new MutableLiveData<>(false);
    private final MutableLiveData<String>  errorMessage = new MutableLiveData<>("");

    private final RegistrationRepository repository;

    public MainHistoryViewModel(RegistrationRepository repository) {
        this.repository = repository;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public LiveData<List<RegistrationHistoryItem>> getRegistrations() { return registrations; }
    public LiveData<Boolean> getIsLoading()                           { return isLoading; }
    public LiveData<String>  getErrorMessage()                        { return errorMessage; }

    /** Initial load — skips fetch if data is already present. */
    public void loadRegistrationHistory() {
        List<RegistrationHistoryItem> current = registrations.getValue();
        if (current != null && !current.isEmpty()) return;
        fetchHistory();
    }

    /** Force re-fetch (called on pull-to-refresh). */
    public void refresh() {
        fetchHistory();
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private void fetchHistory() {
        isLoading.setValue(true);
        errorMessage.setValue("");

        repository.getHistoryForUser((registrationList, error) -> {
            isLoading.postValue(false);

            if (error != null || registrationList == null) {
                String msg = error != null
                        ? error.getMessage()
                        : "Failed to load registration history";
                errorMessage.postValue(msg);
                registrations.postValue(new ArrayList<>());
                return;
            }

            List<RegistrationHistoryItem> items = new ArrayList<>();
            for (Registration r : registrationList) {
                items.add(new RegistrationHistoryItem(
                        r.getEventTitle(),
                        mapStatus(r.getStatus()),
                        r.getTimestamp()
                ));
            }
            registrations.postValue(items);
        });
    }

    /** Maps raw Firestore status strings to human-readable display labels. */
    private static String mapStatus(String status) {
        if (status == null) return "Unknown";
        switch (status.toLowerCase()) {
            case "waitlisted": return "On Waitlist";
            case "invited":
            case "selected":   return "Selected!";
            case "accepted":   return "Enrolled";
            case "declined":   return "Declined";
            case "cancelled":  return "Cancelled";
            default:           return status;
        }
    }

    // ── Data Models ───────────────────────────────────────────────────────────

    /** Raw data object returned by the repository. */
    public static class Registration {
        private final String eventTitle;
        private final String status;
        private final long   timestamp;

        public Registration(String eventTitle, String status, long timestamp) {
            this.eventTitle = eventTitle;
            this.status     = status;
            this.timestamp  = timestamp;
        }

        public String getEventTitle() { return eventTitle; }
        public String getStatus()     { return status; }
        public long   getTimestamp()  { return timestamp; }
    }

    /** Display-ready item consumed by the RecyclerView adapter. */
    public static class RegistrationHistoryItem {
        private final String eventTitle;
        private final String statusLabel;
        private final long   timestamp;

        public RegistrationHistoryItem(String eventTitle, String statusLabel, long timestamp) {
            this.eventTitle  = eventTitle;
            this.statusLabel = statusLabel;
            this.timestamp   = timestamp;
        }

        public String getEventTitle()  { return eventTitle; }
        public String getStatusLabel() { return statusLabel; }
        public long   getTimestamp()   { return timestamp; }
    }

    // ── Repository Interface ──────────────────────────────────────────────────

    public interface RegistrationRepository {
        void getHistoryForUser(HistoryCallback callback);

        interface HistoryCallback {
            void onResult(List<Registration> registrations, Exception error);
        }
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    public static class Factory implements ViewModelProvider.Factory {
        private final RegistrationRepository repository;

        public Factory(RegistrationRepository repository) {
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
}
