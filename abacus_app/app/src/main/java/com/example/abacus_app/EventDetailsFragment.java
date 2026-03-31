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

import java.text.SimpleDateFormat;
import java.util.Locale;

public class EventDetailsFragment extends Fragment {

    public static final String ARG_EVENT_TITLE = "eventTitle";
    public static final String ARG_EVENT_ID    = "eventId";

    /**
     * Optional boolean arg. When true, the fragment immediately triggers the
     * join flow (or guest sign-up) as soon as the user ID is resolved.
     * Set by EventAdapter when the user taps the Join button on a home card.
     */
    public static final String ARG_AUTO_JOIN = "autoJoin";

    private String currentEventId = null;
    private String currentUserId  = null;
    private String eventTitle     = "Event Details";
    private boolean isGuest       = false;
    private boolean autoJoin      = false;

    private Button btnJoinWaitlist;
    private Button btnLeaveWaitlist;
    private TextView tvWaitlistCount;

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;

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

        eventRepository        = new EventRepository();
        registrationRepository = new RegistrationRepository();

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

        registrationRepository.isOnWaitlist(guestKey, currentEventId, isOn -> {
            if (isOn) {
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
        });
    }

    // ── Authenticated waitlist ────────────────────────────────────────────────

    private void setupAuthenticatedWaitlist() {
        UserLocalDataSource localDataSource   = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(
                com.google.firebase.firestore.FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(localDataSource, remoteDataSource);

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
     * Checks registration status via RegistrationRepository and shows
     * the correct button. If autoJoin is set and the user is NOT yet on the
     * waitlist, immediately fires joinWaitlist().
     */
    private void checkWaitlistStatus() {
        if (currentEventId == null) return;
        registrationRepository.isOnWaitlist(currentUserId, currentEventId, isOn -> {
            if (isOn) {
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
        registrationRepository.leaveWaitlist(guestKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "You have left the waiting list.", Toast.LENGTH_SHORT).show();
            View root = getView();
            if (root != null) {
                showJoinButton();
                btnJoinWaitlist.setOnClickListener(v -> openGuestSignUp(root));
            }
            loadWaitlistCount();
        });
    }

    // ── Event details ─────────────────────────────────────────────────────────

    private void loadEventDetails() {
        eventRepository.getEventByIdAsync(currentEventId, event -> {
            if (event == null || getView() == null) return;

            ImageView ivPoster = getView().findViewById(R.id.iv_event_poster);
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

            TextView tvDescription = getView().findViewById(R.id.tv_description);
            if (tvDescription != null && event.getDescription() != null)
                tvDescription.setText(event.getDescription());

            TextView tvDateTime = getView().findViewById(R.id.tv_date_time);
            if (tvDateTime != null && event.getRegistrationStart() != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
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

            // Organizer name — direct Firestore read used here intentionally.
            // UserRepository only exposes getProfile() which fetches the CURRENT
            // user by their locally stored UUID. There is no getUserById(id) method
            // to look up an arbitrary user by ID. Until UserRepository is extended
            // to support that, this lookup stays as a direct Firestore call.
            String organizerId = event.getOrganizerId();
            if (organizerId != null) {
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
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

    // ── Waitlist count ────────────────────────────────────────────────────────

    private void loadWaitlistCount() {
        if (currentEventId == null) return;
        eventRepository.getEventByIdAsync(currentEventId, event -> {
            if (tvWaitlistCount == null || event == null) return;
            int count        = event.getWaitlistCount() != null ? event.getWaitlistCount() : 0;
            Integer capacity = event.getWaitlistCapacity();
            //BUG FIX from part3: Disables button and changes text to EVENT FULL when spotsLeft is 0, only when the join button is currently showing (so the user isnt on the waitlist)
            if (capacity == null) {
                tvWaitlistCount.setText(count + " on waiting list");
            } else {
                long spotsLeft = Math.max(0, capacity - count);
                tvWaitlistCount.setText(count + " on waiting list · " + spotsLeft + " spots left");
                if (spotsLeft == 0 && btnJoinWaitlist != null
                        && btnJoinWaitlist.getVisibility() == View.VISIBLE) {
                    btnJoinWaitlist.setEnabled(false);
                    btnJoinWaitlist.setText("Event Full");
                }
            }
        });
    }

    // ── Join / leave (authenticated) ──────────────────────────────────────────

    private void joinWaitlist() {
        if (currentUserId == null || currentEventId == null) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if already on waitlist, then check capacity before joining
        registrationRepository.isOnWaitlist(currentUserId, currentEventId, isOn -> {
            if (isOn) {
                Toast.makeText(requireContext(),
                        "You are already on the waiting list for this event.",
                        Toast.LENGTH_LONG).show();
                showLeaveButton();
                return;
            }

            registrationRepository.getWaitListSize(currentEventId, count -> {
                if (count == null) count = 0;
                final int finalCount = count;
                eventRepository.getEventByIdAsync(currentEventId, event -> {
                    Integer capacity = event != null ? event.getWaitlistCapacity() : null;
                    if (capacity != null && finalCount >= capacity) {
                        Toast.makeText(requireContext(),
                                "This waiting list is full.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    registrationRepository.joinWaitlist(currentUserId, currentEventId, error -> {
                        if (error != null) {
                            Toast.makeText(requireContext(),
                                    "Something went wrong. Please try again.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Toast.makeText(requireContext(),
                                "You have joined the waiting list!", Toast.LENGTH_SHORT).show();
                        showLeaveButton();
                        loadWaitlistCount();
                    });
                });
            });
        });
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

        registrationRepository.leaveWaitlist(currentUserId, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "You have left the waiting list.", Toast.LENGTH_SHORT).show();
            showJoinButton();
            loadWaitlistCount();
        });
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