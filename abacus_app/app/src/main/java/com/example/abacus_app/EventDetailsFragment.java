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
 *
 * Admin delete: When the effective role is "admin", a red Delete Event button
 * is shown above the join/leave buttons. Tapping it shows a confirmation
 * dialog and performs a soft delete (sets isDeleted=true in Firestore).
 * The waitlist join/leave buttons are hidden for admins since they manage
 * rather than participate.
 */
package com.example.abacus_app;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

public class EventDetailsFragment extends Fragment {

    /** Bundle key for the event title string passed from the previous screen. */
    public static final String ARG_EVENT_TITLE = "eventTitle";

    /** Bundle key for the Firestore event document ID. */
    public static final String ARG_EVENT_ID    = "eventId";

    /**
     * Optional boolean arg. When true, the fragment immediately triggers the
     * join flow (or guest sign-up) as soon as the user ID is resolved.
     * Set by EventAdapter when the user taps the Join button on a home card.
     */
    public static final String ARG_AUTO_JOIN = "autoJoin";

    private String currentEventId = null;
    private String currentUserId  = null;
    private String currentRegistrationKey = null;
    private Event  loadedEvent    = null;
    private String eventTitle     = "Event Details";
    private boolean isGuest       = false;
    private boolean autoJoin      = false;
    private boolean isEditing     = false;

    private FirebaseFirestore db;
    private Button btnJoinWaitlist;
    private Button btnLeaveWaitlist;
    private Button btnAccept;
    private Button btnDecline;
    private TextView tvStatusMessage;
    private Button btnViewMap;
    private ImageButton btnViewQr;
    private TextView tvWaitlistCount;

    private EventRepository eventRepository;
    private RegistrationRepository registrationRepository;
    private NotificationRepository notificationRepository;

    private EditText etTitle, etDescription, etPosterUrl;
    private TextView tvTitle, tvDescription;
    private ImageView ivPoster;

    private FusedLocationProviderClient fusedLocationClient;

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
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        // Hide bottom nav while viewing event details
        ((MainActivity) requireActivity()).showBottomNav(false);

        db                     = FirebaseFirestore.getInstance();
        eventRepository        = new EventRepository();
        registrationRepository = new RegistrationRepository();
        notificationRepository = new NotificationRepository();

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

        tvTitle = view.findViewById(R.id.tv_event_title);
        if (tvTitle != null) tvTitle.setText(eventTitle);

        // ── QR button ─────────────────────────────────────────────────────────
        btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID,   currentEventId != null ? currentEventId : "");
            args.putString(EventQrFragment.ARG_EVENT_NAME, eventTitle);
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });

        // ── Comment Button ─────────────────────────────────────────────────────
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

        ivPoster      = view.findViewById(R.id.iv_event_poster);
        tvDescription = view.findViewById(R.id.tv_description);
        etTitle       = view.findViewById(R.id.et_edit_title);
        etDescription = view.findViewById(R.id.et_edit_description);
        etPosterUrl   = view.findViewById(R.id.et_edit_poster_url);

        if (currentEventId != null) loadEventDetails();

        // ── Waitlist setup ─────────────────────────────────────────────────────
        btnJoinWaitlist  = view.findViewById(R.id.btn_join_waitlist);
        btnLeaveWaitlist = view.findViewById(R.id.btn_leave_waitlist);
        btnAccept = view.findViewById(R.id.btn_accept_invitation);
        btnDecline = view.findViewById(R.id.btn_decline_invitation);
        tvStatusMessage = view.findViewById(R.id.tv_event_waitlist_status_message);
        tvWaitlistCount  = view.findViewById(R.id.tv_waitlist_count);
        btnViewMap       = view.findViewById(R.id.btn_view_map);

        btnJoinWaitlist.setEnabled(false);
        btnLeaveWaitlist.setEnabled(false);
        btnAccept.setEnabled(false);
        btnDecline.setEnabled(false);

        if (btnViewMap != null) {
            btnViewMap.setVisibility(View.GONE);
            btnViewMap.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putString(EventMapFragment.ARG_EVENT_ID, currentEventId);
                args.putString(EventMapFragment.ARG_EVENT_NAME, eventTitle);
                Navigation.findNavController(view).navigate(R.id.eventMapFragment, args);
            });
        }


        if (isGuest) {
            setupGuestWaitlist(view);
        } else {
            setupAuthenticatedWaitlist();
        }

        loadWaitlistCount();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ((MainActivity) requireActivity()).showBottomNav(true);
    }

    // ── Guest waitlist ────────────────────────────────────────────────────────

    /**
     * Sets up waitlist interaction for unauthenticated (guest) users.
     * If a guest email is already stored in SharedPreferences, checks whether
     * the guest is already on the waitlist and shows the appropriate button.
     * If no email is stored, shows the Join button which navigates to sign-up.
     *
     * @param view the fragment's root view, needed to navigate to GuestSignUpFragment
     */
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

    /**
     * Sets up waitlist interaction for authenticated users.
     * Resolves the user's UUID asynchronously, then determines whether to
     * show the Edit button (for organizers/co-organizers) or check waitlist
     * status and show the appropriate join/leave/accept/decline button.
     */
    private void setupAuthenticatedWaitlist() {
        UserLocalDataSource localDataSource   = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(
                FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getCurrentUserId(uuid -> {
            currentUserId = uuid;

            FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
            String email = authUser != null ? authUser.getEmail() : null;
            if (email != null && !email.trim().isEmpty() && uuid == null) {
                currentRegistrationKey = GuestSignUpFragment.emailToKey(
                        email.trim().toLowerCase(Locale.US));
            } else {
                // Fallback if auth email is unavailable.
                currentRegistrationKey = uuid;
            }

            // Re-check now that IDs are resolved — co-organizer needs Edit not Join
            if (canEditCurrentEvent()) {
                showJoinButton(); // showJoinButton checks canEditCurrentEvent and shows "Edit"
            } else {
                checkWaitlistStatus();
            }
        });

        btnJoinWaitlist.setOnClickListener(v -> {
            if (canEditCurrentEvent()) {
                toggleEditMode();
            } else {
                handleJoinFlow();
            }
        });
        btnLeaveWaitlist.setOnClickListener(v -> showLeaveConfirmationDialog());
        btnAccept.setOnClickListener(v -> acceptInvitation());
        btnDecline.setOnClickListener(v -> showDeclineConfirmationDialog());
    }

    /**
     * Checks registration status via RegistrationRepository and shows
     * the correct button. If autoJoin is set and the user is NOT yet on the
     * waitlist, immediately fires handleJoinFlow().
     */
    private void checkWaitlistStatus() {
        if (currentEventId == null || currentRegistrationKey == null) return;
        registrationRepository.isOnWaitlist(currentRegistrationKey, currentEventId, isOn -> {
            if (isOn) {
                registrationRepository.getUserEntry(currentRegistrationKey, currentEventId, entry -> {
                    // show correct button/text depending on waitlist status
                    switch (entry.getStatus()) {
                        case WaitlistEntry.STATUS_WAITLISTED:
                            showLeaveButton();
                            break;
                        case WaitlistEntry.STATUS_INVITED:
                            showAcceptDeclineButtons();
                            break;
                        case WaitlistEntry.STATUS_ACCEPTED:
                            showStatusMessage(WaitlistEntry.STATUS_ACCEPTED);
                            break;
                        case WaitlistEntry.STATUS_DECLINED:
                            showStatusMessage(WaitlistEntry.STATUS_DECLINED);
                            break;
                        case WaitlistEntry.STATUS_CANCELLED:
                            showStatusMessage(WaitlistEntry.STATUS_CANCELLED);
                            break;
                    }
                });
            } else {
                showJoinButton();
                // Auto-join: fire immediately, no extra tap needed
                if (autoJoin && btnJoinWaitlist.getVisibility() == View.VISIBLE) handleJoinFlow();
            }
        });
    }

    // ── Guest routing ─────────────────────────────────────────────────────────

    /**
     * Navigates to GuestSignUpFragment so the guest can register their email
     * before joining the waitlist. Blocks navigation if registration has already ended.
     *
     * @param view the fragment's root view used to find the NavController
     */
    private void openGuestSignUp(@NonNull View view) {
        if (loadedEvent != null && loadedEvent.getRegistrationEnd() != null &&
                loadedEvent.getRegistrationEnd().toDate().before(new Date())) {
            Toast.makeText(getContext(), "Registration has ended", Toast.LENGTH_SHORT).show();
            return;
        }
        Bundle args = new Bundle();
        args.putString(GuestSignUpFragment.ARG_EVENT_ID,    currentEventId != null ? currentEventId : "");
        args.putString(GuestSignUpFragment.ARG_EVENT_TITLE, eventTitle);
        Navigation.findNavController(view).navigate(R.id.guestSignUpFragment, args);
    }

    /**
     * Shows a confirmation dialog before a guest leaves the waitlist.
     *
     * @param guestEmail the stored guest email used to identify the Firestore document
     */
    private void showGuestLeaveConfirmationDialog(@NonNull String guestEmail) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave",  (dialog, which) -> leaveGuestWaitlist(guestEmail))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Removes a guest from the waitlist using their sanitised email key,
     * notifies the organizer, and resets the UI to the Join state.
     *
     * @param guestEmail the stored guest email used to derive the Firestore document key
     */
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

            // Notify organizer that entrant left
            notificationRepository.notifyOrganizerLeftWaitlist(currentEventId, guestKey);

            View root = getView();
            if (root != null) {
                showJoinButton();
                btnJoinWaitlist.setOnClickListener(v -> openGuestSignUp(root));
            }
            loadWaitlistCount();
        });
    }

    // ── Event details ─────────────────────────────────────────────────────────

    /**
     * Fetches the full event document from Firestore and populates all UI fields:
     * poster image, description, event date, registration period, waitlist capacity,
     * and organizer name. Also re-checks waitlist status once the event is loaded
     * so the correct button state is shown for authenticated users.
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
                    this.loadedEvent = event;

                    if (btnViewQr != null) {
                        btnViewQr.setVisibility(event.isPrivate() ? View.GONE : View.VISIBLE);
                    }

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

                    if (btnViewMap != null && event.isGeoRequired()
                            && canEditCurrentEvent()) {
                        btnViewMap.setVisibility(View.VISIBLE);
                    }

                    // ── Delete button ─────────────────────────────────────────────────────────
                    // Shown to: admins (any event) or organizers (their own events)
                    boolean isAdmin = "admin".equals(((MainActivity) requireActivity()).getEffectiveRole());
                    boolean isOrganizerOfThisEvent = canEditCurrentEvent();


                    if (isAdmin || isOrganizerOfThisEvent) {
                        btnLeaveWaitlist.setVisibility(View.GONE);
                        btnJoinWaitlist.setEnabled(true);
                        btnJoinWaitlist.setText("Delete");
                        btnJoinWaitlist.setBackgroundTintList(
                                ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.error_red)));
                        btnJoinWaitlist.setOnClickListener(v ->
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Delete Event")
                                        .setMessage("Remove \"" + eventTitle + "\"? This cannot be undone.")
                                        .setPositiveButton("Delete", (d, w) -> softDeleteFromDetails())
                                        .setNegativeButton("Cancel", null)
                                        .show());
                        return; // skip all waitlist setup below
                    }

                    // Re-apply join button visibility based on loaded event privacy
                    if (currentUserId != null) checkWaitlistStatus();

                    // Organizer name — direct Firestore read used here intentionally.
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

    // ── Waitlist count ────────────────────────────────────────────────────────

    /**
     * Fetches the current waitlist count from Firestore and updates {@code tvWaitlistCount}.
     * If the waitlist is full and the Join button is visible, disables it and
     * changes its text to "Event Full" to prevent further joins.
     */
    private void loadWaitlistCount() {
        if (currentEventId == null) return;
        eventRepository.getEventByIdAsync(currentEventId, event -> {
            if (tvWaitlistCount == null || event == null) return;
            int count        = event.getWaitlistCount() != null ? event.getWaitlistCount() : 0;
            Integer capacity = event.getWaitlistCapacity();
            if (capacity == null) {
                tvWaitlistCount.setText(count + " on waiting list");
            } else {
                long spotsLeft = Math.max(0, capacity - count);
                tvWaitlistCount.setText(count + " on waiting list · " + spotsLeft + " spots left");
                if (spotsLeft == 0 && btnJoinWaitlist != null
                        && btnJoinWaitlist.getVisibility() == View.VISIBLE
                        && !"admin".equals(((MainActivity) requireActivity()).getEffectiveRole())) {
                    btnJoinWaitlist.setEnabled(false);
                    btnJoinWaitlist.setText("Event Full");
                }
            }
        });
    }

    // ── Join / leave (authenticated) ──────────────────────────────────────────

    /**
     * Entry point for the join flow. If the event requires geolocation,
     * checks for location permission and either requests it or proceeds
     * to fetch the location. Otherwise joins immediately with no location.
     */
    private void handleJoinFlow() {
        if (loadedEvent != null && loadedEvent.isGeoRequired()) {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            } else {
                fetchLocationAndJoin();
            }
        } else {
            joinWaitlist(null);
        }
    }

    /**
     * Fetches the device's current location using high-accuracy GPS,
     * falling back to last known location if the current fix is unavailable.
     * Calls joinWaitlist() with the resolved location (or null on failure).
     */
    private void fetchLocationAndJoin() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        Toast.makeText(getContext(), "Joining with location...", Toast.LENGTH_SHORT).show();
        fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        joinWaitlist(location);
                    } else {
                        fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                            if (lastLoc != null) {
                                joinWaitlist(lastLoc);
                            } else {
                                Toast.makeText(getContext(),
                                        "Could not retrieve location. Ensure GPS is enabled.",
                                        Toast.LENGTH_LONG).show();
                                joinWaitlist(null);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> joinWaitlist(null));
    }

    /**
     * Handles the result of the location permission request.
     * Proceeds to fetchLocationAndJoin() if granted, or shows an error toast if denied.
     *
     * @param requestCode  the request code passed to requestPermissions()
     * @param permissions  the requested permissions
     * @param grantResults the grant result for each permission
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 100 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            fetchLocationAndJoin();
        } else if (requestCode == 100) {
            Toast.makeText(getContext(),
                    "Location permission is required to join this event.",
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Adds the current user to the event's waitlist in Firestore via
     * RegistrationRepository. On success, switches the UI to the Leave button
     * and refreshes the waitlist count.
     *
     * @param location the user's current location, or null if geo is not required
     */
    private void joinWaitlist(Location location) {
        if (currentRegistrationKey == null || currentEventId == null) {
            Toast.makeText(requireContext(), "Something went wrong.", Toast.LENGTH_SHORT).show();
            return;
        }

        registrationRepository.joinWaitlist(currentRegistrationKey, currentEventId, location, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(requireContext(),
                    "You have joined the waiting list!",
                    Toast.LENGTH_SHORT).show();

            showLeaveButton();
            loadWaitlistCount();
        });
    }

    /**
     * Shows a confirmation dialog before an authenticated user leaves the waitlist.
     */
    private void showLeaveConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Leave Waiting List")
                .setMessage("Are you sure you want to leave the waiting list for this event?")
                .setPositiveButton("Leave",  (dialog, which) -> leaveWaitlist())
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Removes the authenticated user from the waitlist in Firestore,
     * notifies the organizer, and resets the UI to the Join state.
     */
    private void leaveWaitlist() {
        if (currentRegistrationKey == null || currentEventId == null) return;

        registrationRepository.leaveWaitlist(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "You have left the waiting list.", Toast.LENGTH_SHORT).show();

            // Notify organizer that entrant left
            notificationRepository.notifyOrganizerLeftWaitlist(currentEventId, currentRegistrationKey);

            showJoinButton();
            loadWaitlistCount();
        });
    }

    /**
     * Shows a confirmation dialog before the user declines their lottery invitation.
     * Warns that the decision is irreversible.
     */
    private void showDeclineConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Decline Invitation?")
                .setMessage("Are you sure you want to decline your invitation to this event? Once you do, you cannot change your mind.")
                .setPositiveButton("YES",  (dialog, which) -> declineInvitation())
                .setNegativeButton("NO", (dialog, which) -> dialog.dismiss())
                .show();
    }

    /**
     * Records the user's decision to decline their lottery invitation in Firestore,
     * notifies the organizer, and updates the UI to show the declined status message.
     */
    private void declineInvitation() {
        if (currentRegistrationKey == null || currentEventId == null) return;
        registrationRepository.declineInvitation(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "Declined.", Toast.LENGTH_SHORT).show();

            // Notify organizer that entrant declined
            notificationRepository.notifyOrganizerDecline(currentEventId, currentRegistrationKey);

            showStatusMessage(WaitlistEntry.STATUS_DECLINED);
            loadWaitlistCount();
        });
    }

    /**
     * Records the user's decision to accept their lottery invitation in Firestore
     * and updates the UI to show the accepted status message.
     */
    private void acceptInvitation() {
        if (currentRegistrationKey == null || currentEventId == null) return;
        registrationRepository.acceptInvitation(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(),
                    "Accepted.", Toast.LENGTH_SHORT).show();
            showStatusMessage(WaitlistEntry.STATUS_ACCEPTED);
            loadWaitlistCount();
        });
    }

    // ── Button visibility ─────────────────────────────────────────────────────

    /**
     * Shows the Join Waiting List button and hides all others.
     * If the current user is the organizer or co-organizer, shows "Edit" instead.
     * If registration has ended, disables the button and shows "Registration Ended".
     * If the event is private and the user is not the organizer, hides the button.
     */
    private void showJoinButton() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.VISIBLE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage != null) tvStatusMessage.setVisibility(View.GONE);

        if (canEditCurrentEvent()) {
            if (btnJoinWaitlist != null) {
                btnJoinWaitlist.setEnabled(true);
                btnJoinWaitlist.setText(isEditing ? "Save Changes" : "Edit");
            }
            return;
        }

        // US: Private events are invite-only. If not already on waitlist/invited, hide join button.
        if (loadedEvent != null && loadedEvent.isPrivate()) {
            if (btnJoinWaitlist != null) btnJoinWaitlist.setVisibility(View.GONE);
            return;
        }

        if (btnJoinWaitlist != null) {
            boolean isEnded = loadedEvent != null
                    && loadedEvent.getRegistrationEnd() != null
                    && loadedEvent.getRegistrationEnd().toDate().before(new Date());
            btnJoinWaitlist.setEnabled(currentUserId != null && !isEnded);
            btnJoinWaitlist.setText(isEnded ? "Registration Ended" : "Join Waiting List");
        }
    }

    /**
     * Shows the Leave Waiting List button and hides all others.
     */
    private void showLeaveButton() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.VISIBLE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setEnabled(true);
        if (btnAccept != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage != null) tvStatusMessage.setVisibility(View.GONE);
    }

    /**
     * Shows the Accept and Decline invitation buttons and hides all others.
     * Used when the user's waitlist status is STATUS_INVITED.
     */
    private void showAcceptDeclineButtons() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.VISIBLE);
        if (btnDecline != null) btnDecline.setVisibility(View.VISIBLE);
        if (tvStatusMessage != null) tvStatusMessage.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setEnabled(true);
        if (btnDecline != null) btnDecline.setEnabled(true);
    }

    /**
     * Hides all action buttons and displays a coloured status message.
     * Used for terminal states: accepted, declined, or cancelled.
     *
     * @param status one of {@link WaitlistEntry#STATUS_ACCEPTED},
     *               {@link WaitlistEntry#STATUS_DECLINED}, or
     *               {@link WaitlistEntry#STATUS_CANCELLED}
     */
    private void showStatusMessage(String status) {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage != null) tvStatusMessage.setVisibility(View.VISIBLE);

        switch (status) {
            case WaitlistEntry.STATUS_ACCEPTED:
                tvStatusMessage.setText("Congratulations! You are going to this event.");
                tvStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_status_accepted_green));
                break;
            case WaitlistEntry.STATUS_DECLINED:
                tvStatusMessage.setText("You have declined your invitation to this event.");
                tvStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_status_declined_red));
                break;
            case WaitlistEntry.STATUS_CANCELLED:
                tvStatusMessage.setText("Sorry, your invitation to this event has been cancelled.");
                tvStatusMessage.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_status_canceled_black));
                break;
        }
    }

    // ── Edit mode (organizer) ─────────────────────────────────────────────────

    /**
     * Returns true if the current user is allowed to edit this event.
     * Editing is permitted for: the event's organizer (matched by UUID or Firebase UID)
     * when they hold the organizer or admin role, or any co-organizer by Firebase UID.
     *
     * @return true if the user can edit the event, false otherwise
     */
    private boolean canEditCurrentEvent() {
        if (loadedEvent == null || getActivity() == null) return false;
        if (!(getActivity() instanceof MainActivity)) return false;

        String role = ((MainActivity) getActivity()).getEffectiveRole();
        boolean hasOrganizerRole = "organizer".equals(role) || "admin".equals(role);

        boolean ownsEvent = false;
        if (currentUserId != null && currentUserId.equals(loadedEvent.getOrganizerId())) {
            ownsEvent = true;
        } else {
            com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null && firebaseUser.getUid().equals(loadedEvent.getOrganizerId())) {
                ownsEvent = true;
            }
        }

        // Co-organizer check — coOrganizers stores Firebase UID
        boolean isCoOrganizer = false;
        if (loadedEvent.getCoOrganizers() != null) {
            com.google.firebase.auth.FirebaseUser fbUser =
                    com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null && loadedEvent.getCoOrganizers().contains(fbUser.getUid())) {
                isCoOrganizer = true;
            }
        }

        return (hasOrganizerRole && ownsEvent) || isCoOrganizer;
    }

    /**
     * Toggles the event details screen between view mode and edit mode.
     * In edit mode, text fields replace the read-only TextViews and the
     * bottom button changes to "Save Changes". Calling again while editing
     * triggers saveChanges().
     */
    private void toggleEditMode() {
        if (loadedEvent == null) return;
        if (!isEditing) {
            isEditing = true;
            btnJoinWaitlist.setText("Save Changes");
            if (tvTitle != null) tvTitle.setVisibility(View.GONE);
            if (tvDescription != null) tvDescription.setVisibility(View.GONE);
            if (etTitle != null) {
                etTitle.setVisibility(View.VISIBLE);
                etTitle.setText(loadedEvent.getTitle());
            }
            if (etDescription != null) {
                etDescription.setVisibility(View.VISIBLE);
                etDescription.setText(loadedEvent.getDescription());
            }
            if (etPosterUrl != null) {
                etPosterUrl.setVisibility(View.VISIBLE);
                etPosterUrl.setText(loadedEvent.getPosterImageUrl());
            }
        } else {
            saveChanges();
        }
    }

    /**
     * Persists the organizer's edits (title, description, poster URL) to Firestore.
     * Validates that the title is non-empty before saving. On success, exits edit
     * mode and reloads the event details.
     */
    private void saveChanges() {
        if (loadedEvent == null || currentEventId == null) return;
        String newTitle = etTitle != null
                ? etTitle.getText().toString().trim() : loadedEvent.getTitle();
        String newDescription = etDescription != null
                ? etDescription.getText().toString().trim() : loadedEvent.getDescription();
        String newPosterUrl = etPosterUrl != null
                ? etPosterUrl.getText().toString().trim() : loadedEvent.getPosterImageUrl();
        if (newTitle.isEmpty()) {
            Toast.makeText(requireContext(), "Title cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        loadedEvent.setTitle(newTitle);
        loadedEvent.setDescription(newDescription);
        loadedEvent.setPosterImageUrl(newPosterUrl);
        db.collection("events").document(currentEventId).set(loadedEvent)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(),
                            "Event updated successfully", Toast.LENGTH_SHORT).show();
                    isEditing = false;
                    exitEditMode();
                    loadEventDetails();
                });
    }

    // ── Admin soft delete ─────────────────────────────────────────────────────

    /**
     * Performs a soft delete of the current event by setting isDeleted=true in Firestore.
     * The real-time snapshot listener in MainActivity automatically removes the event
     * from the browse list on next update. Navigates back on success.
     */
    private void softDeleteFromDetails() {
        if (currentEventId == null) return;
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(currentEventId)
                .update("isDeleted", true)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(),
                            "Event deleted.", Toast.LENGTH_SHORT).show();
                    if (!Navigation.findNavController(requireView()).popBackStack()) {
                        ((MainActivity) requireActivity()).showHome();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to delete event.", Toast.LENGTH_SHORT).show()
                );
    }

    /**
     * Restores the event details screen to view mode after editing is complete.
     * Hides the edit text fields and shows the read-only TextViews.
     */
    private void exitEditMode() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setText("Edit");
        if (tvTitle          != null) tvTitle.setVisibility(View.VISIBLE);
        if (tvDescription    != null) tvDescription.setVisibility(View.VISIBLE);
        if (etTitle          != null) etTitle.setVisibility(View.GONE);
        if (etDescription    != null) etDescription.setVisibility(View.GONE);
        if (etPosterUrl      != null) etPosterUrl.setVisibility(View.GONE);
    }
}