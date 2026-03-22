package com.example.abacus_app;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Architecture Layer: ViewModel
 *
 * Fetches and holds the current user's registration history from Firestore.
 * Maps raw status strings to human-readable display labels before exposing
 * them to the fragment.
 *
 * Used by: RegistrationHistoryFragment
 */
public class RegistrationHistoryViewModel extends ViewModel {

    private final MutableLiveData<List<RegistrationHistoryItem>> registrations = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");

    private final RegistrationRepository repository;

    public RegistrationHistoryViewModel(RegistrationRepository repository) {
        this.repository = repository;
    }

    // ─── Public API ──────────────────────────────────────────────────────────────

    public LiveData<List<RegistrationHistoryItem>> getRegistrations() { return registrations; }
    public LiveData<Boolean> getIsLoading()                           { return isLoading; }
    public LiveData<String> getErrorMessage()                         { return errorMessage; }

    /** Initial load — skips fetch if data is already present. */
    public void loadRegistrationHistory() {
        List<RegistrationHistoryItem> current = registrations.getValue();
        if (current != null && !current.isEmpty()) return;
        fetchHistory();
    }

    /** Force re-fetch (called on swipe-to-refresh). */
    public void refresh() {
        fetchHistory();
    }

    // ─── Private ─────────────────────────────────────────────────────────────────

    private void fetchHistory() {
        isLoading.setValue(true);
        errorMessage.setValue("");

        repository.getHistoryForUser((registrationList, error) -> {
            isLoading.postValue(false);

            if (error != null || registrationList == null) {
                String msg = error != null ? error.getMessage() : "Failed to load registration history";
                errorMessage.postValue(msg);
                registrations.postValue(new ArrayList<>());
                return;
            }

            List<RegistrationHistoryItem> items = new ArrayList<>();
            for (Registration r : registrationList) {
                items.add(new RegistrationHistoryItem(
                        r.getEventId(),
                        r.getEventTitle(),
                        r.getPosterImageUrl(),
                        mapStatus(r.getStatus()),
                        r.getRegisteredAt()
                ));
            }
            registrations.postValue(items);
        });
    }

    /** Maps raw Firestore status strings to human-readable labels. */
    private static String mapStatus(String status) {
        if (status == null) return "Unknown";
        switch (status.toLowerCase()) {
            case "waitlisted": return "On Waitlist";
            case "invited":    // Firestore status when user is drawn in lottery
            case "selected":   return "Selected!";
            case "accepted":   return "Enrolled";
            case "declined":   return "Declined";
            case "cancelled":  return "Cancelled";
            default:           return status;
        }
    }

    // ─── Data Models ─────────────────────────────────────────────────────────────

    /** Raw data object returned by the repository. */
    public static class Registration {
        private String eventId;
        private String eventTitle;
        private String posterImageUrl;
        private String status;
        private long registeredAt;

        public Registration() {}

        public Registration(String eventId, String eventTitle, String posterImageUrl,
                            String status, long registeredAt) {
            this.eventId        = eventId;
            this.eventTitle     = eventTitle;
            this.posterImageUrl = posterImageUrl;
            this.status         = status;
            this.registeredAt   = registeredAt;
        }

        public String getEventId()        { return eventId; }
        public String getEventTitle()     { return eventTitle; }
        public String getPosterImageUrl() { return posterImageUrl; }
        public String getStatus()         { return status; }
        public long   getRegisteredAt()   { return registeredAt; }
    }

    /** Display-ready item consumed by the RecyclerView adapter. */
    public static class RegistrationHistoryItem {
        private final String eventId;
        private final String eventTitle;
        private final String posterImageUrl;
        private final String statusLabel;
        private final long   timestamp;

        public RegistrationHistoryItem(String eventId, String eventTitle, String posterImageUrl,
                                       String statusLabel, long timestamp) {
            this.eventId        = eventId;
            this.eventTitle     = eventTitle;
            this.posterImageUrl = posterImageUrl;
            this.statusLabel    = statusLabel;
            this.timestamp      = timestamp;
        }

        public String getEventId()        { return eventId; }
        public String getEventTitle()     { return eventTitle; }
        public String getPosterImageUrl() { return posterImageUrl; }
        public String getStatusLabel()    { return statusLabel; }
        public long   getTimestamp()      { return timestamp; }
    }

    // ─── Repository Interface ────────────────────────────────────────────────────

    public interface RegistrationRepository {
        void getHistoryForUser(HistoryCallback callback);

        interface HistoryCallback {
            void onResult(List<Registration> registrations, Exception error);
        }
    }

    // ─── Factory ─────────────────────────────────────────────────────────────────

    public static class Factory implements ViewModelProvider.Factory {
        private final RegistrationRepository repository;

        public Factory(RegistrationRepository repository) {
            this.repository = repository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(RegistrationHistoryViewModel.class)) {
                return (T) new RegistrationHistoryViewModel(repository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class");
        }
    }
}