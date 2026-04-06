/**
 * LotteryGuidelinesFragment.java
 *
 * Role: Displays the lottery selection guidelines for a specific event.
 * Accessible via the "Lottery Guidelines" button on EventDetailsFragment.
 * Guidelines are loaded from Firestore if available, with sensible defaults
 * shown when no custom criteria have been set by the organizer.
 *
 * User story: As an entrant, I want to be informed about the criteria or
 * guidelines for the lottery selection process.
 *
 * Outstanding issues:
 * - Guidelines are currently shown with default text.
 * - When organizers can set custom lottery info, replace defaults with
 *   Firestore fields from the event document.
 */
package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class LotteryGuidelinesFragment extends Fragment {

    /** Bundle key for the event ID passed from EventDetailsFragment. */
    public static final String ARG_EVENT_ID = "eventId";

    private String currentEventId = null;

    /**
     * Inflates the lottery guidelines layout.
     *
     * @param inflater           the LayoutInflater used to inflate the layout
     * @param container          the parent ViewGroup, or null if there is no parent
     * @param savedInstanceState the previously saved instance state, or null
     * @return the inflated root view for this fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lottery_guidelines, container, false);
    }

    /**
     * Reads the event ID argument, wires up the back button, and initiates the
     * Firestore fetch for lottery guidelines. Falls back to default text if no
     * event ID is provided.
     *
     * @param view               the root view returned by {@link #onCreateView}
     * @param savedInstanceState the previously saved instance state, or null
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        currentEventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID, null)
                : null;

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back_guidelines);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                ((MainActivity) requireActivity()).showHome();
            }
        });

        // Load lottery info from Firestore, fall back to defaults
        if (currentEventId != null) {
            loadLotteryGuidelines(view);
        } else {
            populateDefaults(view, null);
        }
    }

    /**
     * Fetches event data from Firestore and populates the guidelines UI.
     * Uses the event's waitlistCapacity and registrationEnd as lottery info.
     * Falls back to default text for fields not stored on the event document.
     *
     * @param view the fragment's root view
     */
    private void loadLotteryGuidelines(View view) {
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(currentEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists() || !isAdded()) return;
                    Event event = snapshot.toObject(Event.class);
                    populateDefaults(view, event);
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) populateDefaults(view, null);
                });
    }

    /**
     * Populates all guideline text fields.
     * Uses real event data where available, defaults otherwise.
     *
     * @param view  the fragment's root view
     * @param event the Event object from Firestore, or null if unavailable
     */
    private void populateDefaults(View view, @Nullable Event event) {
        TextView tvSelectionProcess = view.findViewById(R.id.tv_selection_process);
        TextView tvEntrantsSelected = view.findViewById(R.id.tv_entrants_selected);
        TextView tvDrawDate         = view.findViewById(R.id.tv_draw_date);
        TextView tvIfSelected       = view.findViewById(R.id.tv_if_selected);
        TextView tvIfDeclined       = view.findViewById(R.id.tv_if_declined);
        TextView tvEligibility      = view.findViewById(R.id.tv_eligibility);

        // Selection process — always random unless organizer sets criteria
        tvSelectionProcess.setText(
                "Selection is entirely random. All entrants on the waiting list have an equal " +
                        "chance of being chosen. No additional criteria are used in the selection process.");

        // Number of entrants selected — based on waitlist capacity if available
        if (event != null && event.getWaitlistCapacity() != null) {
            tvEntrantsSelected.setText(event.getWaitlistCapacity() +
                    " entrant(s) will be selected from the waiting list.");
        } else {
            tvEntrantsSelected.setText(
                    "The number of entrants selected will be determined by the organizer " +
                            "based on available capacity.");
        }

        // Lottery draw date — based on registrationEnd if available
        if (event != null && event.getRegistrationEnd() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
            String drawDate = sdf.format(event.getRegistrationEnd().toDate());
            tvDrawDate.setText("The lottery draw will occur on " + drawDate +
                    ", after the registration period closes.");
        } else {
            tvDrawDate.setText(
                    "The lottery draw will occur after the registration period closes. " +
                            "You will be notified of the exact date.");
        }

        // If selected
        tvIfSelected.setText(
                "If you are selected, you will receive a notification through the app. " +
                        "You must confirm your spot within the time period specified by the organizer. " +
                        "Failure to respond within this window may result in your spot being forfeited.");

        // If declined or no response
        tvIfDeclined.setText(
                "If a selected entrant declines or does not respond within the required time, " +
                        "their spot will be offered to the next entrant on the waiting list. " +
                        "This process continues until all spots are filled.");

        // Eligibility
        tvEligibility.setText(
                "There are no additional eligibility requirements for this event. " +
                        "Any entrant on the waiting list is eligible to be selected.");
    }
}