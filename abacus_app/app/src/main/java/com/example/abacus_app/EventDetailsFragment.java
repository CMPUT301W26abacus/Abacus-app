/**
 * EventDetailsFragment.java
 *
 * Role: Displays the full details of a selected event. Accessible by tapping
 * any event card from the main browse list. Provides buttons to view the
 * event's QR code, lottery guidelines, and join/leave the waiting list.
 * Bottom navigation is hidden on this screen for a focused experience.
 *
 * Design pattern: Fragment (View layer). Logic lives directly in the fragment
 * with no ViewModel, consistent with the rest of the project architecture.
 *
 * Outstanding issues:
 * - waitlistCount is read directly from the event document as a Long rather
 *   than through a dedicated field on Event.java (Himesh's model does not
 *   include this field yet).
 * - tv_location is not populated because Event.java has no location field.
 * - Organizer name lookup makes a second Firestore read on every load;
 *   could be cached once the user model is stable.
 */
package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class EventDetailsFragment extends Fragment {

    /** Bundle key for the event title passed from MainActivity. */
    public static final String ARG_EVENT_TITLE = "eventTitle";

    /** Bundle key for the Firestore event document ID passed from MainActivity. */
    public static final String ARG_EVENT_ID    = "eventId";

    private String currentEventId = null;
    private String currentUserId  = null;

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

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                ((MainActivity) requireActivity()).showHome();
            }
        });

        // ── Read args from Bundle ──────────────────────────────────────────────

        String eventTitle = getArguments() != null
                ? getArguments().getString(ARG_EVENT_TITLE, "Event Details")
                : "Event Details";

        currentEventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID, null)
                : null;

        // ── Show event title ───────────────────────────────────────────────────
        TextView tvTitle = view.findViewById(R.id.tv_event_title);
        if (tvTitle != null) tvTitle.setText(eventTitle);

        // ── QR button — uses real event ID and title ───────────────────────────
        ImageButton btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID,   currentEventId != null ? currentEventId : "");
            args.putString(EventQrFragment.ARG_EVENT_NAME, eventTitle);
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });

        // ── Lottery Guidelines button ──────────────────────────────────────────
        Button btnLotteryGuidelines = view.findViewById(R.id.btn_lottery_guidelines);
        btnLotteryGuidelines.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(LotteryGuidelinesFragment.ARG_EVENT_ID,
                    currentEventId != null ? currentEventId : "");
            Navigation.findNavController(view).navigate(R.id.lotteryGuidelinesFragment, args);
        });

        // ── Load full event details from Firestore ─────────────────────────────
        if (currentEventId != null) {
            loadEventDetails();
        }

        // ── Waitlist logic (US 01.01.01, US 01.01.02) ─────────────────────────

        db               = FirebaseFirestore.getInstance();
        btnJoinWaitlist  = view.findViewById(R.id.btn_join_waitlist);
        btnLeaveWaitlist = view.findViewById(R.id.btn_leave_waitlist);
        tvWaitlistCount  = view.findViewById(R.id.tv_waitlist_count);

        btnJoinWaitlist.setEnabled(false);
        btnLeaveWaitlist.setEnabled(false);

        UserLocalDataSource localDataSource = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getCurrentUserId(uuid -> {
            currentUserId = uuid;
            btnJoinWaitlist.setEnabled(true);
            btnLeaveWaitlist.setEnabled(true);
            checkWaitlistStatus();
            loadWaitlistCount();
        });

        btnJoinWaitlist.setOnClickListener(v -> joinWaitlist());
        btnLeaveWaitlist.setOnClickListener(v -> showLeaveConfirmationDialog());
    }

    /**
     * Loads full event details from Firestore and populates all UI fields
     * including poster image, description, date range, waitlist capacity,
     * and organizer name.
     *
     * Organizer name is resolved by a secondary lookup in the users/ collection
     * using the event's organizerId field.
     */
    private void loadEventDetails() {
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(currentEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;
                    Event event = snapshot.toObject(Event.class);
                    if (event == null) return;

                    // ── Poster image ───────────────────────────────────────────
                    ImageView ivPoster = getView() != null
                            ? getView().findViewById(R.id.iv_event_poster) : null;
                    if (ivPoster != null) {
                        String posterUrl = event.getPosterImageUrl();
                        if (posterUrl != null && !posterUrl.isEmpty()) {
                            Glide.with(this)
                                    .load(posterUrl)
                                    .placeholder(R.drawable.ic_event_poster)
                                    .error(R.drawable.ic_event_poster)
                                    .centerCrop()
                                    .into(ivPoster);
                        } else {
                            ivPoster.setImageResource(R.drawable.ic_event_poster);
                        }
                    }

                    // ── Description ────────────────────────────────────────────
                    TextView tvDescription = getView() != null
                            ? getView().findViewById(R.id.tv_description) : null;
                    if (tvDescription != null && event.getDescription() != null) {
                        tvDescription.setText(event.getDescription());
                    }

                    // ── Date range ─────────────────────────────────────────────
                    TextView tvDateTime = getView() != null
                            ? getView().findViewById(R.id.tv_date_time) : null;
                    if (tvDateTime != null && event.getRegistrationStart() != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                        String start = sdf.format(event.getRegistrationStart().toDate());
                        if (event.getRegistrationEnd() != null) {
                            String end = sdf.format(event.getRegistrationEnd().toDate());
                            tvDateTime.setText(start + " – " + end);
                        } else {
                            tvDateTime.setText(start);
                        }
                    }

                    // ── Waitlist capacity ──────────────────────────────────────
                    if (tvWaitlistCount != null) {
                        Integer capacity = event.getWaitlistCapacity();
                        if (capacity == null) {
                            tvWaitlistCount.setText("Unlimited spots");
                        } else {
                            tvWaitlistCount.setText("Capacity: " + capacity);
                        }
                    }

                    // ── Organizer name ─────────────────────────────────────────
                    String organizerId = event.getOrganizerId();
                    if (organizerId != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(organizerId)
                                .get()
                                .addOnSuccessListener(userSnapshot -> {
                                    TextView tvOrganizer = getView() != null
                                            ? getView().findViewById(R.id.tv_organizer) : null;
                                    if (tvOrganizer != null) {
                                        if (userSnapshot.exists()) {
                                            String name = userSnapshot.getString("name");
                                            tvOrganizer.setText("Hosted by " +
                                                    (name != null ? name : "Unknown"));
                                        } else {
                                            tvOrganizer.setText("Hosted by Unknown");
                                        }
                                    }
                                });
                    }
                });
    }

    /**
     * Checks Firestore to determine whether the current user is already on the
     * waiting list for this event, then shows the appropriate button.
     *
     * US 01.01.01 AC 1 — Leave button shown if already on waitlist.
     * US 01.01.02 AC 4 — Join button shown if not on waitlist.
     */
    private void checkWaitlistStatus() {
        if (currentEventId == null) return;
        db.collection("registrations")
                .document(currentUserId + "_" + currentEventId)
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
     * Fetches the current waitlist count and capacity from Firestore and
     * updates the tv_waitlist_count TextView. Called on load and after
     * any join or leave action.
     */
    private void loadWaitlistCount() {
        if (currentEventId == null) return;
        db.collection("events")
                .document(currentEventId)
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
     * Attempts to add the current user to the waiting list for this event.
     *
     * Creates a registration document at registrations/{userId}_{eventId}
     * and increments the event's waitlistCount field atomically.
     *
     * US 01.01.01 AC 1 — Adds entrant and shows confirmation toast.
     * US 01.01.01 AC 2 — Prevents duplicate registration with error toast.
     * US 01.01.01 AC 3 — Registration persisted in Firestore across restarts.
     */
    private void joinWaitlist() {
        if (currentUserId == null || currentEventId == null) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference registrationRef = db.collection("registrations")
                .document(currentUserId + "_" + currentEventId);
        DocumentReference eventRef = db.collection("events")
                .document(currentEventId);

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
                    registration.put("userId", currentUserId);
                    registration.put("eventId", currentEventId);
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
     * Shows a confirmation dialog before removing the user from the waiting list.
     *
     * US 01.01.02 AC 2 — Confirmation dialog shown before removal.
     * US 01.01.02 AC 5 — Cancelling leaves waitlist status unchanged.
     */
    private void showLeaveConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave", (dialog, which) -> leaveWaitlist())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Removes the current user's registration from Firestore and decrements
     * the event's waitlistCount field atomically.
     *
     * US 01.01.02 AC 1 — Removes entrant from waiting list.
     * US 01.01.02 AC 3 — Waitlist count updated after removal.
     */
    private void leaveWaitlist() {
        if (currentUserId == null || currentEventId == null) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference registrationRef = db.collection("registrations")
                .document(currentUserId + "_" + currentEventId);
        DocumentReference eventRef = db.collection("events")
                .document(currentEventId);

        registrationRef.delete()
                .addOnSuccessListener(unused -> {
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

    /**
     * Shows the Join Waiting List button and hides the Leave button.
     * Called when the user is confirmed to not be on the waitlist.
     */
    private void showJoinButton() {
        btnJoinWaitlist.setVisibility(View.VISIBLE);
        btnLeaveWaitlist.setVisibility(View.GONE);
    }

    /**
     * Shows the Leave Waiting List button and hides the Join button.
     * Called when the user is confirmed to be on the waitlist.
     */
    private void showLeaveButton() {
        btnJoinWaitlist.setVisibility(View.GONE);
        btnLeaveWaitlist.setVisibility(View.VISIBLE);
    }
}