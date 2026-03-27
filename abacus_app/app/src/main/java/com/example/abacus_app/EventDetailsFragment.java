/**
 * EventDetailsFragment.java
 *
 * Role: Displays the full details of a selected event. Provides buttons to
 * view the event's QR code, lottery guidelines, and join/leave the waiting list.
 *
 * Auto-join: When ARG_AUTO_JOIN=true is passed in the Bundle (set by
 * EventAdapter when the user taps the Join button on the home screen card),
 * the fragment fires joinWaitlist() or openGuestSignUp() automatically as
 * soon as the user ID is resolved — no extra tap needed.
 */
package com.example.abacus_app;

import android.content.Context;
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
import java.util.Map;
import java.util.Locale;

public class EventDetailsFragment extends Fragment {

    public static final String ARG_EVENT_TITLE = "eventTitle";
    public static final String ARG_EVENT_ID    = "eventId";

    /**
     * Optional boolean arg. When true, the fragment immediately triggers the
     * join flow (or guest sign-up) as soon as the user ID is resolved.
     * Set by EventAdapter when the user taps the Join button on a home card.
     */
    public static final String ARG_AUTO_JOIN   = "autoJoin";

    private String currentEventId = null;
    private String currentUserId  = null;
    private String eventTitle     = "Event Details";
    private boolean isGuest       = false;
    private boolean autoJoin      = false;

    private FirebaseFirestore db;
    private Button btnJoinWaitlist;
    private Button btnLeaveWaitlist;
    private TextView tvWaitlistCount;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

        isGuest  = requireActivity().getIntent().getBooleanExtra("isGuest", false);
        autoJoin = getArguments() != null && getArguments().getBoolean(ARG_AUTO_JOIN, false);

        // ── Back ───────────────────────────────────────────────────────────────
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                ((MainActivity) requireActivity()).showHome();
            }
        });

        // ── Args ───────────────────────────────────────────────────────────────
        eventTitle = getArguments() != null
                ? getArguments().getString(ARG_EVENT_TITLE, "Event Details")
                : "Event Details";
        currentEventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID, null)
                : null;

        TextView tvTitle = view.findViewById(R.id.tv_event_title);
        if (tvTitle != null) tvTitle.setText(eventTitle);

        // ── QR button ─────────────────────────────────────────────────────────
        ImageButton btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID,   currentEventId != null ? currentEventId : "");
            args.putString(EventQrFragment.ARG_EVENT_NAME, eventTitle);
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });
        // ── Comment Button ─────────────────────────────────────────────────
        Button btnViewComments = view.findViewById(R.id.btn_view_comments);
        btnViewComments.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(CommentsFragment.ARG_EVENT_ID, currentEventId != null ? currentEventId : "");
            CommentsFragment commentsFragment = new CommentsFragment();
            commentsFragment.setArguments(args);
            commentsFragment.show(getParentFragmentManager(), "comments");
        });
        // ── Lottery Guidelines ─────────────────────────────────────────────────
        Button btnLotteryGuidelines = view.findViewById(R.id.btn_lottery_guidelines);
        btnLotteryGuidelines.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(LotteryGuidelinesFragment.ARG_EVENT_ID,
                    currentEventId != null ? currentEventId : "");
            Navigation.findNavController(view).navigate(R.id.lotteryGuidelinesFragment, args);
        });

        if (currentEventId != null) loadEventDetails();

        // ── Waitlist setup ─────────────────────────────────────────────────────
        db               = FirebaseFirestore.getInstance();
        btnJoinWaitlist  = view.findViewById(R.id.btn_join_waitlist);
        btnLeaveWaitlist = view.findViewById(R.id.btn_leave_waitlist);
        tvWaitlistCount  = view.findViewById(R.id.tv_waitlist_count);

        btnJoinWaitlist.setEnabled(false);
        btnLeaveWaitlist.setEnabled(false);

        if (isGuest) {
            setupGuestWaitlist(view);
        } else {
            setupAuthenticatedWaitlist();
        }

        loadWaitlistCount();
    }

    // ── Guest waitlist ────────────────────────────────────────────────────────

    private void setupGuestWaitlist(@NonNull View view) {
        String savedEmail = requireContext()
                .getSharedPreferences(GuestSignUpFragment.PREFS_GUEST, Context.MODE_PRIVATE)
                .getString(GuestSignUpFragment.PREF_GUEST_EMAIL, null);

        if (savedEmail == null) {
            showJoinButton();
            btnJoinWaitlist.setEnabled(true);
            btnJoinWaitlist.setOnClickListener(v -> openGuestSignUp(view));
            // Auto-join for guests with no email goes straight to sign-up form
            if (autoJoin) openGuestSignUp(view);
            return;
        }

        String guestKey = GuestSignUpFragment.emailToKey(savedEmail);
        String docId    = guestKey + "_" + currentEventId;

        db.collection("registrations").document(docId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        showLeaveButton();
                        btnLeaveWaitlist.setEnabled(true);
                        btnLeaveWaitlist.setOnClickListener(v ->
                                showGuestLeaveConfirmationDialog(savedEmail));
                    } else {
                        showJoinButton();
                        btnJoinWaitlist.setEnabled(true);
                        btnJoinWaitlist.setOnClickListener(v -> openGuestSignUp(view));
                        // Auto-join: go straight to guest sign-up form
                        if (autoJoin) openGuestSignUp(view);
                    }
                })
                .addOnFailureListener(e -> {
                    showJoinButton();
                    btnJoinWaitlist.setEnabled(true);
                    btnJoinWaitlist.setOnClickListener(v -> openGuestSignUp(view));
                });
    }

    // ── Authenticated waitlist ────────────────────────────────────────────────

    private void setupAuthenticatedWaitlist() {
        UserLocalDataSource localDataSource   = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository         = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getCurrentUserId(uuid -> {
            currentUserId = uuid;
            btnJoinWaitlist.setEnabled(true);
            btnLeaveWaitlist.setEnabled(true);
            checkWaitlistStatus();
        });

        btnJoinWaitlist.setOnClickListener(v -> joinWaitlist());
        btnLeaveWaitlist.setOnClickListener(v -> showLeaveConfirmationDialog());
    }

    /**
     * Checks Firestore for the current user's registration status and shows
     * the correct button. If autoJoin is set and the user is NOT yet on the
     * waitlist, immediately fires joinWaitlist().
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
                        // Auto-join: fire immediately, no extra tap needed
                        if (autoJoin) joinWaitlist();
                    }
                });
    }

    // ── Guest routing ─────────────────────────────────────────────────────────

    private void openGuestSignUp(@NonNull View view) {
        Bundle args = new Bundle();
        args.putString(GuestSignUpFragment.ARG_EVENT_ID,    currentEventId != null ? currentEventId : "");
        args.putString(GuestSignUpFragment.ARG_EVENT_TITLE, eventTitle);
        Navigation.findNavController(view).navigate(R.id.guestSignUpFragment, args);
    }

    private void showGuestLeaveConfirmationDialog(@NonNull String guestEmail) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave",  (dialog, which) -> leaveGuestWaitlist(guestEmail))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void leaveGuestWaitlist(@NonNull String guestEmail) {
        if (currentEventId == null) return;

        String guestKey = GuestSignUpFragment.emailToKey(guestEmail);
        String docId    = guestKey + "_" + currentEventId;

        DocumentReference regRef      = db.collection("registrations").document(docId);
        DocumentReference eventRef    = db.collection("events").document(currentEventId);
        DocumentReference waitlistRef = db.collection("events")
                .document(currentEventId).collection("waitlist").document(guestKey);

        regRef.delete()
                .addOnSuccessListener(unused ->
                        waitlistRef.delete()
                                .addOnSuccessListener(unused2 -> {
                                    eventRef.update("waitlistCount", FieldValue.increment(-1));
                                    Toast.makeText(requireContext(),
                                            "You have left the waiting list.",
                                            Toast.LENGTH_SHORT).show();
                                    View root = getView();
                                    if (root != null) {
                                        showJoinButton();
                                        btnJoinWaitlist.setOnClickListener(
                                                v -> openGuestSignUp(root));
                                    }
                                    loadWaitlistCount();
                                })
                                .addOnFailureListener(e ->
                                        Toast.makeText(requireContext(),
                                                "Something went wrong. Please try again.",
                                                Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Something went wrong. Please try again.",
                                Toast.LENGTH_SHORT).show());
    }

    // ── Firestore: event details ──────────────────────────────────────────────

    private void loadEventDetails() {
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(currentEventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;
                    Event event = snapshot.toObject(Event.class);
                    if (event == null) return;

                    ImageView ivPoster = getView() != null
                            ? getView().findViewById(R.id.iv_event_poster) : null;
                    if (ivPoster != null) {
                        String posterUrl = event.getPosterImageUrl();
                        if (posterUrl != null && !posterUrl.isEmpty()) {
                            Glide.with(this).load(posterUrl)
                                    .placeholder(R.drawable.ic_event_poster)
                                    .error(R.drawable.ic_event_poster)
                                    .centerCrop().into(ivPoster);
                        } else {
                            ivPoster.setImageResource(R.drawable.ic_event_poster);
                        }
                    }

                    TextView tvDescription = getView() != null
                            ? getView().findViewById(R.id.tv_description) : null;
                    if (tvDescription != null && event.getDescription() != null)
                        tvDescription.setText(event.getDescription());

                    TextView tvDateTime = getView() != null
                            ? getView().findViewById(R.id.tv_date_time) : null;
                    if (tvDateTime != null && event.getRegistrationStart() != null) {
                        SimpleDateFormat sdf =
                                new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
                        String start = sdf.format(event.getRegistrationStart().toDate());
                        tvDateTime.setText(event.getRegistrationEnd() != null
                                ? start + " – " + sdf.format(event.getRegistrationEnd().toDate())
                                : start);
                    }

                    if (tvWaitlistCount != null) {
                        Integer capacity = event.getWaitlistCapacity();
                        tvWaitlistCount.setText(capacity == null
                                ? "Unlimited spots" : "Capacity: " + capacity);
                    }

                    String organizerId = event.getOrganizerId();
                    if (organizerId != null) {
                        FirebaseFirestore.getInstance()
                                .collection("users").document(organizerId).get()
                                .addOnSuccessListener(userSnapshot -> {
                                    TextView tvOrganizer = getView() != null
                                            ? getView().findViewById(R.id.tv_organizer) : null;
                                    if (tvOrganizer != null) {
                                        String name = userSnapshot.exists()
                                                ? userSnapshot.getString("name") : null;
                                        tvOrganizer.setText("Hosted by " +
                                                (name != null ? name : "Unknown"));
                                    }
                                });
                    }
                });
    }

    // ── Firestore: waitlist count ─────────────────────────────────────────────

    private void loadWaitlistCount() {
        if (currentEventId == null) return;
        db.collection("events").document(currentEventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && tvWaitlistCount != null) {
                        Long count    = snapshot.getLong("waitlistCount");
                        Long capacity = snapshot.getLong("waitlistCapacity");
                        if (count == null) count = 0L;
                        if (capacity == null || capacity == -1) {
                            tvWaitlistCount.setText(count + " on waiting list");
                        } else {
                            long spotsLeft = Math.max(0, capacity - count);
                            tvWaitlistCount.setText(count + " on waiting list · "
                                    + spotsLeft + " spots left");
                        }
                    }
                });
    }

    // ── Firestore: join / leave (authenticated) ───────────────────────────────

    private void joinWaitlist() {
        if (currentUserId == null || currentEventId == null) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference registrationRef = db.collection("registrations")
                .document(currentUserId + "_" + currentEventId);
        DocumentReference eventRef = db.collection("events").document(currentEventId);

        registrationRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Toast.makeText(requireContext(),
                        "You are already on the waiting list for this event.",
                        Toast.LENGTH_LONG).show();
                showLeaveButton();
                return;
            }

            eventRef.get().addOnSuccessListener(eventSnapshot -> {
                Long count    = eventSnapshot.getLong("waitlistCount");
                Long capacity = eventSnapshot.getLong("waitlistCapacity");
                if (count == null) count = 0L;
                if (capacity != null && capacity != -1 && count >= capacity) {
                    Toast.makeText(requireContext(),
                            "This waiting list is full.", Toast.LENGTH_LONG).show();
                    return;
                }

                Map<String, Object> registration = new HashMap<>();
                registration.put("userId",    currentUserId);
                registration.put("eventId",   currentEventId);
                registration.put("status",    "waitlisted");
                registration.put("timestamp", System.currentTimeMillis());

                registrationRef.set(registration).addOnSuccessListener(unused -> {
                    DocumentReference waitlistRef = db.collection("events")
                            .document(currentEventId)
                            .collection("waitlist").document(currentUserId);

                    Map<String, Object> waitlistEntry = new HashMap<>();
                    waitlistEntry.put("userID",        currentUserId);
                    waitlistEntry.put("eventID",       currentEventId);
                    waitlistEntry.put("status",        "waitlisted");
                    waitlistEntry.put("joinTime",      com.google.firebase.Timestamp.now());
                    waitlistEntry.put("lotteryNumber", 0);

                    waitlistRef.set(waitlistEntry)
                            .addOnSuccessListener(unused2 -> {
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
                                            Toast.LENGTH_SHORT).show());
                }).addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Something went wrong. Please try again.",
                                Toast.LENGTH_SHORT).show());

            }).addOnFailureListener(e ->
                    Toast.makeText(requireContext(),
                            "Something went wrong. Please try again.",
                            Toast.LENGTH_SHORT).show());

        }).addOnFailureListener(e ->
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show());
    }

    private void showLeaveConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave",  (dialog, which) -> leaveWaitlist())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void leaveWaitlist() {
        if (currentUserId == null || currentEventId == null) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentReference registrationRef = db.collection("registrations")
                .document(currentUserId + "_" + currentEventId);
        DocumentReference eventRef = db.collection("events").document(currentEventId);
        DocumentReference waitlistRef = db.collection("events")
                .document(currentEventId).collection("waitlist").document(currentUserId);

        registrationRef.delete()
                .addOnSuccessListener(unused ->
                        waitlistRef.delete()
                                .addOnSuccessListener(unused2 -> {
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
                                                Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Something went wrong. Please try again.",
                                Toast.LENGTH_SHORT).show());
    }

    // ── Button visibility ─────────────────────────────────────────────────────

    private void showJoinButton() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.VISIBLE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setEnabled(true);
    }

    private void showLeaveButton() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.VISIBLE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setEnabled(true);
    }
}