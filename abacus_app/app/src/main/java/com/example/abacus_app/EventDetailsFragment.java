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
 * Admin delete: When the effective role is "admin", btnJoinWaitlist is
 * repurposed as a red DELETE button. Tapping it shows a confirmation dialog
 * and performs a soft delete (sets isDeleted=true in Firestore).
 *
 * Organizer edit: When the current user is the organizer or co-organizer,
 * btnJoinWaitlist is repurposed as an orange Edit button. Tapping it toggles
 * inline edit mode for title, description, and poster URL.
 *
 * The waitlist join/leave buttons are hidden for admins and organizers since
 * they manage rather than participate.
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
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class EventDetailsFragment extends Fragment {

    public static final String ARG_EVENT_TITLE = "eventTitle";
    public static final String ARG_EVENT_ID    = "eventId";

    public static final String ARG_AUTO_JOIN = "autoJoin";

    private String currentEventId         = null;
    private String currentUserId          = null;
    private String currentRegistrationKey = null;
    private Event  loadedEvent            = null;
    private String eventTitle             = "Event Details";
    private boolean isGuest               = false;
    private boolean autoJoin              = false;
    private boolean isEditing             = false;

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

    private EditText etTitle, etDescription, etPosterUrl;
    private TextView tvTitle, tvDescription;
    private ImageView ivPoster;

    private FusedLocationProviderClient fusedLocationClient;

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

        db                     = FirebaseFirestore.getInstance();
        eventRepository        = new EventRepository();
        registrationRepository = new RegistrationRepository();

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                ((MainActivity) requireActivity()).showHome();
            }
        });

        eventTitle = getArguments() != null
                ? getArguments().getString(ARG_EVENT_TITLE, "Event Details")
                : "Event Details";
        currentEventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID, null)
                : null;

        tvTitle = view.findViewById(R.id.tv_event_title);
        if (tvTitle != null) tvTitle.setText(eventTitle);

        btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID,   currentEventId != null ? currentEventId : "");
            args.putString(EventQrFragment.ARG_EVENT_NAME, eventTitle);
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });

        Button btnViewComments = view.findViewById(R.id.btn_view_comments);
        btnViewComments.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(CommentsFragment.ARG_EVENT_ID, currentEventId != null ? currentEventId : "");
            CommentsFragment commentsFragment = new CommentsFragment();
            commentsFragment.setArguments(args);
            commentsFragment.show(getParentFragmentManager(), "comments");
        });

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

        btnJoinWaitlist  = view.findViewById(R.id.btn_join_waitlist);
        btnLeaveWaitlist = view.findViewById(R.id.btn_leave_waitlist);
        btnAccept        = view.findViewById(R.id.btn_accept_invitation);
        btnDecline       = view.findViewById(R.id.btn_decline_invitation);
        tvStatusMessage  = view.findViewById(R.id.tv_event_waitlist_status_message);
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
                args.putString(EventMapFragment.ARG_EVENT_ID,   currentEventId);
                args.putString(EventMapFragment.ARG_EVENT_NAME, eventTitle);
                Navigation.findNavController(view).navigate(R.id.eventMapFragment, args);
            });
        }

        if (currentEventId != null) loadEventDetails();

        if (isGuest) {
            setupGuestWaitlist(view);
        } else {
            setupAuthenticatedWaitlist();
        }

        loadWaitlistCount();
    }

    // ── Role helper ───────────────────────────────────────────────────────────

    /**
     * Returns true if the current user has the organizer or admin role.
     * Used to suppress waitlist buttons for organizers on events they don't own.
     */
    private boolean isOrganizerRole() {
        if (getActivity() == null) return false;
        String role = ((MainActivity) getActivity()).getEffectiveRole();
        return "organizer".equals(role) || "admin".equals(role);
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
                if (autoJoin) openGuestSignUp(view);
            }
        });
    }

    // ── Authenticated waitlist ────────────────────────────────────────────────

    private void setupAuthenticatedWaitlist() {
        UserLocalDataSource localDataSource   = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository         = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getCurrentUserId(uuid -> {
            currentUserId = uuid;

            FirebaseUser authUser = FirebaseAuth.getInstance().getCurrentUser();
            String email = authUser != null ? authUser.getEmail() : null;
            if (email != null && !email.trim().isEmpty() && uuid == null) {
                currentRegistrationKey = GuestSignUpFragment.emailToKey(
                        email.trim().toLowerCase(Locale.US));
            } else {
                currentRegistrationKey = uuid;
            }

            // Only call checkWaitlistStatus() if:
            // 1. loadedEvent is already set (so canEditCurrentEvent() is accurate)
            // 2. The user is not the organizer/owner of this event
            // 3. The user does not have the organizer/admin role at all
            if (loadedEvent != null && !canEditCurrentEvent() && !isOrganizerRole()) {
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
     * Checks registration status and shows the correct button.
     * If autoJoin is set and the user is NOT yet on the waitlist,
     * immediately fires handleJoinFlow().
     */
    private void checkWaitlistStatus() {
        if (currentEventId == null || currentRegistrationKey == null) return;
        registrationRepository.isOnWaitlist(currentRegistrationKey, currentEventId, isOn -> {
            if (isOn) {
                registrationRepository.getUserEntry(currentRegistrationKey, currentEventId, entry -> {
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
                if (autoJoin && btnJoinWaitlist.getVisibility() == View.VISIBLE) handleJoinFlow();
            }
        });
    }

    // ── Guest routing ─────────────────────────────────────────────────────────

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
                        "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "You have left the waiting list.", Toast.LENGTH_SHORT).show();
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
     * Loads the event from Firestore and populates all UI fields.
     * Determines which action button to show based on role:
     * - Admin     → btnJoinWaitlist repurposed as red DELETE button
     * - Organizer (owner/co-organizer) → btnJoinWaitlist repurposed as orange Edit button
     * - Organizer (not owner) → all waitlist buttons hidden
     * - Regular   → normal waitlist buttons via checkWaitlistStatus()
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

                    SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

                    TextView tvEventDateTime = getView() != null
                            ? getView().findViewById(R.id.tv_event_date_time) : null;
                    if (tvEventDateTime != null) {
                        if (event.getEventStart() != null) {
                            String start = sdf.format(event.getEventStart().toDate());
                            tvEventDateTime.setText(event.getEventEnd() != null
                                    ? start + " – " + sdf.format(event.getEventEnd().toDate())
                                    : start);
                        } else {
                            tvEventDateTime.setText("Not set");
                        }
                    }

                    TextView tvRegDateTime = getView() != null
                            ? getView().findViewById(R.id.tv_date_time) : null;
                    if (tvRegDateTime != null && event.getRegistrationStart() != null) {
                        String start = sdf.format(event.getRegistrationStart().toDate());
                        tvRegDateTime.setText(event.getRegistrationEnd() != null
                                ? start + " – " + sdf.format(event.getRegistrationEnd().toDate())
                                : start);
                    }

                    if (tvWaitlistCount != null) {
                        Integer capacity = event.getWaitlistCapacity();
                        tvWaitlistCount.setText(capacity == null
                                ? "Unlimited spots" : "Capacity: " + capacity);
                    }

                    if (btnViewMap != null && event.isGeoRequired() && canEditCurrentEvent()) {
                        btnViewMap.setVisibility(View.VISIBLE);
                    }

                    // ── Role-based action button ───────────────────────────────
                    boolean isAdminRole       = "admin".equals(((MainActivity) requireActivity()).getEffectiveRole());
                    boolean isOrganizerOfThis = canEditCurrentEvent();

                    if (isAdminRole) {
                        hideAllActionButtons();
                        btnJoinWaitlist.setVisibility(View.VISIBLE);
                        btnJoinWaitlist.setEnabled(true);
                        btnJoinWaitlist.setText("DELETE");
                        btnJoinWaitlist.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.white));
                        btnJoinWaitlist.setBackgroundTintList(
                                ColorStateList.valueOf(
                                        ContextCompat.getColor(requireContext(), R.color.error_red)));
                        btnJoinWaitlist.setOnClickListener(v ->
                                new AlertDialog.Builder(requireContext())
                                        .setTitle("Delete Event")
                                        .setMessage("Remove \"" + eventTitle + "\"? This cannot be undone.")
                                        .setPositiveButton("Delete", (d, w) -> softDeleteFromDetails())
                                        .setNegativeButton("Cancel", null)
                                        .show());

                    } else if (isOrganizerOfThis) {
                        // Owner/co-organizer of this event: show Edit button
                        hideAllActionButtons();
                        btnJoinWaitlist.setVisibility(View.VISIBLE);
                        btnJoinWaitlist.setEnabled(true);
                        btnJoinWaitlist.setText(isEditing ? "Save Changes" : "Edit");
                        btnJoinWaitlist.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.white));
                        btnJoinWaitlist.setBackgroundTintList(
                                ColorStateList.valueOf(
                                        ContextCompat.getColor(requireContext(), R.color.orange)));
                        btnJoinWaitlist.setOnClickListener(v -> toggleEditMode());

                    } else if (isOrganizerRole()) {
                        // Organizer role but does NOT own this event: hide everything
                        hideAllActionButtons();

                    } else if (currentRegistrationKey != null) {
                        // Regular user: show waitlist buttons
                        checkWaitlistStatus();
                    }
                    // If currentRegistrationKey is still null here, the getUserId
                    // callback in setupAuthenticatedWaitlist() will call
                    // checkWaitlistStatus() once it resolves.

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
                // Only disable join button for regular users — not for admins/organizers
                if (spotsLeft == 0 && btnJoinWaitlist != null
                        && btnJoinWaitlist.getVisibility() == View.VISIBLE
                        && !isOrganizerRole()) {
                    btnJoinWaitlist.setEnabled(false);
                    btnJoinWaitlist.setText("Event Full");
                }
            }
        });
    }

    // ── Join / leave (authenticated) ──────────────────────────────────────────

    private void handleJoinFlow() {
        if (loadedEvent != null && loadedEvent.isGeoRequired()) {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            } else {
                fetchLocationAndJoin();
            }
        } else {
            joinWaitlist(null);
        }
    }

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

    private void joinWaitlist(Location location) {
        if (currentRegistrationKey == null || currentEventId == null) {
            Toast.makeText(requireContext(), "Something went wrong.", Toast.LENGTH_SHORT).show();
            return;
        }
        registrationRepository.joinWaitlist(currentRegistrationKey, currentEventId, location, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "You have joined the waiting list!", Toast.LENGTH_SHORT).show();
            showLeaveButton();
            loadWaitlistCount();
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
        if (currentRegistrationKey == null || currentEventId == null) return;
        registrationRepository.leaveWaitlist(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "You have left the waiting list.", Toast.LENGTH_SHORT).show();
            showJoinButton();
            loadWaitlistCount();
        });
    }

    private void showDeclineConfirmationDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Decline Invitation?")
                .setMessage("Are you sure you want to decline your invitation to this event? Once you do, you cannot change your mind.")
                .setPositiveButton("YES",  (dialog, which) -> declineInvitation())
                .setNegativeButton("NO",   (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void declineInvitation() {
        if (currentRegistrationKey == null || currentEventId == null) return;
        registrationRepository.declineInvitation(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "Declined.", Toast.LENGTH_SHORT).show();
            showStatusMessage(WaitlistEntry.STATUS_DECLINED);
            loadWaitlistCount();
        });
    }

    private void acceptInvitation() {
        if (currentRegistrationKey == null || currentEventId == null) return;
        registrationRepository.acceptInvitation(currentRegistrationKey, currentEventId, error -> {
            if (error != null) {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(requireContext(), "Accepted.", Toast.LENGTH_SHORT).show();
            showStatusMessage(WaitlistEntry.STATUS_ACCEPTED);
            loadWaitlistCount();
        });
    }

    // ── Button visibility helpers ─────────────────────────────────────────────

    private void hideAllActionButtons() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept        != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline       != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage  != null) tvStatusMessage.setVisibility(View.GONE);
    }

    /**
     * Shows the Join Waiting List button for regular users only.
     * Admins and organizers are blocked here as a safety net in case
     * checkWaitlistStatus() is somehow reached for those roles.
     */
    private void showJoinButton() {
        // Safety net: admins and organizers should never see the join button
        if (isOrganizerRole()) {
            hideAllActionButtons();
            return;
        }

        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.VISIBLE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept        != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline       != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage  != null) tvStatusMessage.setVisibility(View.GONE);

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

    private void showLeaveButton() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) { btnLeaveWaitlist.setVisibility(View.VISIBLE); btnLeaveWaitlist.setEnabled(true); }
        if (btnAccept        != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline       != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage  != null) tvStatusMessage.setVisibility(View.GONE);
    }

    private void showAcceptDeclineButtons() {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept        != null) { btnAccept.setVisibility(View.VISIBLE); btnAccept.setEnabled(true); }
        if (btnDecline       != null) { btnDecline.setVisibility(View.VISIBLE); btnDecline.setEnabled(true); }
        if (tvStatusMessage  != null) tvStatusMessage.setVisibility(View.GONE);
    }

    private void showStatusMessage(String status) {
        if (btnJoinWaitlist  != null) btnJoinWaitlist.setVisibility(View.GONE);
        if (btnLeaveWaitlist != null) btnLeaveWaitlist.setVisibility(View.GONE);
        if (btnAccept        != null) btnAccept.setVisibility(View.GONE);
        if (btnDecline       != null) btnDecline.setVisibility(View.GONE);
        if (tvStatusMessage  != null) tvStatusMessage.setVisibility(View.VISIBLE);

        if (tvStatusMessage == null) return;
        switch (status) {
            case WaitlistEntry.STATUS_ACCEPTED:
                tvStatusMessage.setText("Congratulations! You are going to this event.");
                tvStatusMessage.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.color_status_accepted_green));
                break;
            case WaitlistEntry.STATUS_DECLINED:
                tvStatusMessage.setText("You have declined your invitation to this event.");
                tvStatusMessage.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.color_status_declined_red));
                break;
            case WaitlistEntry.STATUS_CANCELLED:
                tvStatusMessage.setText("Sorry, your invitation to this event has been cancelled.");
                tvStatusMessage.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.color_status_canceled_black));
                break;
        }
    }

    // ── Edit mode (organizer) ─────────────────────────────────────────────────

    private boolean canEditCurrentEvent() {
        if (loadedEvent == null || getActivity() == null) return false;
        if (!(getActivity() instanceof MainActivity)) return false;

        String role = ((MainActivity) getActivity()).getEffectiveRole();
        boolean hasOrganizerRole = "organizer".equals(role) || "admin".equals(role);

        boolean ownsEvent = false;
        if (currentUserId != null && currentUserId.equals(loadedEvent.getOrganizerId())) {
            ownsEvent = true;
        } else {
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            if (firebaseUser != null && firebaseUser.getUid().equals(loadedEvent.getOrganizerId())) {
                ownsEvent = true;
            }
        }

        boolean isCoOrganizer = false;
        if (loadedEvent.getCoOrganizers() != null) {
            FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
            if (fbUser != null && loadedEvent.getCoOrganizers().contains(fbUser.getUid())) {
                isCoOrganizer = true;
            }
        }

        return (hasOrganizerRole && ownsEvent) || isCoOrganizer;
    }

    private void toggleEditMode() {
        if (loadedEvent == null) return;
        if (!isEditing) {
            isEditing = true;
            btnJoinWaitlist.setText("Save Changes");
            if (tvTitle       != null) tvTitle.setVisibility(View.GONE);
            if (tvDescription != null) tvDescription.setVisibility(View.GONE);

            if (etTitle != null) {
                etTitle.setVisibility(View.VISIBLE);
                etTitle.setText(loadedEvent.getTitle());
                // Single-line with Done button on keyboard
                etTitle.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                etTitle.setSingleLine(true);
                etTitle.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        saveChanges();
                        return true;
                    }
                    return false;
                });
            }

            if (etDescription != null) {
                etDescription.setVisibility(View.VISIBLE);
                etDescription.setText(loadedEvent.getDescription());
                // Multi-line description gets Done button that submits rather than newline
                etDescription.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                etDescription.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        saveChanges();
                        return true;
                    }
                    return false;
                });
            }

            if (etPosterUrl != null) {
                etPosterUrl.setVisibility(View.VISIBLE);
                etPosterUrl.setText(loadedEvent.getPosterImageUrl());
                etPosterUrl.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
                etPosterUrl.setSingleLine(true);
                etPosterUrl.setOnEditorActionListener((v, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        saveChanges();
                        return true;
                    }
                    return false;
                });
            }
        } else {
            saveChanges();
        }
    }

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

        // Dismiss keyboard immediately
        View focusedView = requireActivity().getCurrentFocus();
        if (focusedView != null) {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
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

                    // Update UI immediately with new values — no reload needed
                    if (tvTitle != null) tvTitle.setText(newTitle);
                    if (tvDescription != null) tvDescription.setText(newDescription);
                    if (ivPoster != null && newPosterUrl != null && !newPosterUrl.isEmpty()) {
                        Glide.with(this).load(newPosterUrl)
                                .placeholder(R.drawable.ic_event_poster)
                                .error(R.drawable.ic_event_poster)
                                .centerCrop().into(ivPoster);
                    } else if (ivPoster != null) {
                        ivPoster.setImageResource(R.drawable.ic_event_poster);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to save changes. Please try again.", Toast.LENGTH_SHORT).show());
    }

    // ── Admin soft delete ─────────────────────────────────────────────────────

    private void softDeleteFromDetails() {
        if (currentEventId == null) return;
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(currentEventId)
                .update("isDeleted", true)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(requireContext(), "Event deleted.", Toast.LENGTH_SHORT).show();
                    if (!Navigation.findNavController(requireView()).popBackStack()) {
                        ((MainActivity) requireActivity()).showHome();
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to delete event.", Toast.LENGTH_SHORT).show());
    }

    private void exitEditMode() {
        if (btnJoinWaitlist != null) btnJoinWaitlist.setText("Edit");
        if (tvTitle         != null) tvTitle.setVisibility(View.VISIBLE);
        if (tvDescription   != null) tvDescription.setVisibility(View.VISIBLE);
        if (etTitle         != null) etTitle.setVisibility(View.GONE);
        if (etDescription   != null) etDescription.setVisibility(View.GONE);
        if (etPosterUrl     != null) etPosterUrl.setVisibility(View.GONE);
    }
}