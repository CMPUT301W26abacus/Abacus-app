package com.example.abacus_app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.firebase.firestore.DocumentSnapshot;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages UI state and business logic for tracking entrants and finalizing the lottery.
 * Owner: Himesh
 */
public class ManageEventViewModel extends ViewModel {
    private final RegistrationRemoteDataSource registrationDataSource;
    
    private final MutableLiveData<List<WaitlistEntry>> entrants = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public ManageEventViewModel() {
        this.registrationDataSource = new RegistrationRemoteDataSource();
    }

    public LiveData<List<WaitlistEntry>> getEntrants() { return entrants; }
    public LiveData<String> getError() { return error; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }

    public void loadWaitlist(String eventId) {
        isLoading.setValue(true);
        registrationDataSource.getWaitlist(eventId).addOnSuccessListener(querySnapshot -> {
            List<WaitlistEntry> list = new ArrayList<>();
            for (DocumentSnapshot doc : querySnapshot) {
                WaitlistEntry entry = doc.toObject(WaitlistEntry.class);
                if (entry != null) list.add(entry);
            }
            entrants.setValue(list);
            isLoading.setValue(false);
        }).addOnFailureListener(e -> {
            isLoading.setValue(false);
            error.setValue("Failed to load waitlist: " + e.getMessage());
        });
    }

    // US 02.06.04: Cancel entrants that did not sign up
    public void cancelEntrant(String eventId, String userId) {
        // Implementation logic for updating status in Firestore via repository
    }

    // US 02.06.05: Export final list to CSV
    public void exportToCSV(List<WaitlistEntry> entries) {
        // Implementation for CSV formatting and saving
    }
}
