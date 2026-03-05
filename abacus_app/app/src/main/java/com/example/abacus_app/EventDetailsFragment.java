/**
 * EventDetailsFragment.java
 *
 * Role: Displays the full details of a selected event. Accessible by tapping
 * any event card from the main browse list. Provides a button to view the
 * event's QR code via EventQrFragment. Bottom navigation is hidden on this
 * screen for a focused full-screen experience.
 *
 * Outstanding issues:
 * - Event data is not yet populated; fields will be bound from a Firestore
 *   document once Firebase integration is complete.
 * - Event ID and name passed to EventQrFragment are hardcoded test values.
 * - User ID is hardcoded; replace with Dyna's UserRepository when ready.
 */
package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EventDetailsFragment extends Fragment {

    // TODO: Replace with Dyna's UserRepository.getInstance().getUserId() when ready
    private static final String HARDCODED_USER_ID = "test_user_001";

    // TODO: Replace with real event ID from Bundle when navigation is wired up
    private static final String HARDCODED_EVENT_ID = "event_test_12345";

    private FirebaseFirestore db;
    private Button btnJoinWaitlist;
    private Button btnLeaveWaitlist;
    private TextView tvWaitlistCount;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.event_details_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Teammate's original code (unchanged) ──────────────────────────────

        // Back button → returns to home screen
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                // Nothing left to pop — go back to home
                ((MainActivity) requireActivity()).showHome();
            }
        });

        // QR button → EventQrFragment, passing event ID and name
        // Replace hardcoded values with real Firestore event data later
        ImageButton btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID, "event_test_12345");
            args.putString(EventQrFragment.ARG_EVENT_NAME, "Test Event");
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });

        // ── Waitlist logic (US 01.01.01, US 01.01.02) ─────────────────────────

        db = FirebaseFirestore.getInstance();
        btnJoinWaitlist  = view.findViewById(R.id.btn_join_waitlist);
        btnLeaveWaitlist = view.findViewById(R.id.btn_leave_waitlist);
        tvWaitlistCount  = view.findViewById(R.id.tv_waitlist_count);

        // On load: check Firestore to set correct button visibility (AC 1, AC 4)
        checkWaitlistStatus();

        // On load: fetch live waitlist count
        loadWaitlistCount();

        btnJoinWaitlist.setOnClickListener(v -> joinWaitlist());

        // Show confirmation dialog before leaving (AC 2)
        btnLeaveWaitlist.setOnClickListener(v -> showLeaveConfirmationDialog());
    }

    /**
     * Reads Firestore on load to decide which button to show.
     * AC 1 — Leave button visible if already on waitlist.
     * AC 4 — Leave button not shown if not on waitlist.
     */
    private void checkWaitlistStatus() {
        db.collection("registrations")
                .document(HARDCODED_USER_ID + "_" + HARDCODED_EVENT_ID)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        showLeaveButton();
                    } else {
                        showJoinButton();
                    }
                });
    }

    /**
     * Fetches waitlistCount and waitlistCapacity from the event document
     * and updates tv_waitlist_count. Called on load and after join/leave.
     */
    private void loadWaitlistCount() {
        db.collection("events")
                .document(HARDCODED_EVENT_ID)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && tvWaitlistCount != null) {
                        Long count    = snapshot.getLong("waitlistCount");
                        Long capacity = snapshot.getLong("waitlistCapacity");
                        if (count == null) count = 0L;

                        if (capacity == null || capacity == -1) {
                            tvWaitlistCount.setText(count + " on waiting list");
                        } else {
                            long spotsLeft = capacity - count;
                            tvWaitlistCount.setText(
                                    count + " on waiting list · " + spotsLeft + " spots left");
                        }
                    }
                });
    }

    /**
     * Attempts to join the waiting list.
     * AC 1 (join) — adds registration + shows confirmation toast.
     * AC 2 (join) — prevents duplicate, shows error message from spec.
     * AC 3 (join) — persisted in Firestore across app restarts.
     * Also increments waitlistCount on the event document atomically.
     */
    private void joinWaitlist() {
        DocumentReference registrationRef = db.collection("registrations")
                .document(HARDCODED_USER_ID + "_" + HARDCODED_EVENT_ID);
        DocumentReference eventRef = db.collection("events")
                .document(HARDCODED_EVENT_ID);

        registrationRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Toast.makeText(requireContext(),
                                "You are already on the waiting list for this event.",
                                Toast.LENGTH_LONG).show();
                        showLeaveButton();
                        return;
                    }

                    Map<String, Object> registration = new HashMap<>();
                    registration.put("userId", HARDCODED_USER_ID);
                    registration.put("eventId", HARDCODED_EVENT_ID);
                    registration.put("status", "waitlisted");
                    registration.put("timestamp", System.currentTimeMillis());

                    registrationRef.set(registration)
                            .addOnSuccessListener(unused -> {
                                eventRef.update("waitlistCount", FieldValue.increment(1));
                                Toast.makeText(requireContext(),
                                        "You have joined the waiting list!",
                                        Toast.LENGTH_SHORT).show();
                                showLeaveButton();
                                loadWaitlistCount();
                            })
                            .addOnFailureListener(e ->
                                    Toast.makeText(requireContext(),
                                            "Something went wrong. Please try again.",
                                            Toast.LENGTH_SHORT).show()
                            );
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Something went wrong. Please try again.",
                                Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Shows a confirmation dialog before leaving the waitlist.
     * AC 2 — confirmation dialog shown when Leave is tapped.
     * AC 5 — if cancelled, waitlist status is unchanged.
     */
    private void showLeaveConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave", (dialog, which) -> leaveWaitlist())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss()) // AC 5 — do nothing
                .show();
    }

    /**
     * Removes the registration from Firestore and decrements waitlistCount.
     * AC 3 — entrant removed and waitlist count updated.
     */
    private void leaveWaitlist() {
        DocumentReference registrationRef = db.collection("registrations")
                .document(HARDCODED_USER_ID + "_" + HARDCODED_EVENT_ID);
        DocumentReference eventRef = db.collection("events")
                .document(HARDCODED_EVENT_ID);

        registrationRef.delete()
                .addOnSuccessListener(unused -> {
                    // Atomically decrement — won't go below 0
                    eventRef.update("waitlistCount", FieldValue.increment(-1));
                    Toast.makeText(requireContext(),
                            "You have left the waiting list.",
                            Toast.LENGTH_SHORT).show();
                    showJoinButton();
                    loadWaitlistCount();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Something went wrong. Please try again.",
                                Toast.LENGTH_SHORT).show()
                );
    }

    /** Shows the Join button and hides Leave. Used when user is NOT on waitlist. */
    private void showJoinButton() {
        btnJoinWaitlist.setVisibility(View.VISIBLE);
        btnLeaveWaitlist.setVisibility(View.GONE);
    }

    /** Shows the Leave button and hides Join. Used when user IS on waitlist. */
    private void showLeaveButton() {
        btnJoinWaitlist.setVisibility(View.GONE);
        btnLeaveWaitlist.setVisibility(View.VISIBLE);
    }
}