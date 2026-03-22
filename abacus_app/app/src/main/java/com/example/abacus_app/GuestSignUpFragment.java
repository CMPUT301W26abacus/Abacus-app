/**
 * GuestSignUpFragment.java
 *
 * Role: Shown when a guest user taps "Join Waiting List" on an event they
 * haven't signed in for. Collects a mandatory name and email, then writes a
 * guest registration document to Firestore so the organiser can see interest
 * without requiring a full account.
 *
 * After a successful join, the guest's email is persisted to SharedPreferences
 * under the key "guest_email" so that EventDetailsFragment can check duplicate
 * status across fragment re-creations without asking the user again.
 *
 * Navigation: Reached from EventDetailsFragment when isGuest == true.
 * "Create account?" navigates to RegisterActivity.
 *
 * Firestore writes:
 *   registrations/{guestKey}_{eventId}     — status: "guest_waitlisted"
 *   events/{eventId}/waitlist/{guestKey}   — mirrors registration for lottery
 *   events/{eventId}.waitlistCount         — incremented atomically
 */
package com.example.abacus_app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GuestSignUpFragment extends Fragment {

    /** Bundle key for the Firestore event document ID. */
    public static final String ARG_EVENT_ID    = "eventId";

    /** Bundle key for the event title (shown in header). */
    public static final String ARG_EVENT_TITLE = "eventTitle";

    /** SharedPreferences file name shared with EventDetailsFragment. */
    public static final String PREFS_GUEST     = "guest_prefs";

    /** SharedPreferences key for the guest's email address. */
    public static final String PREF_GUEST_EMAIL = "guest_email";

    private String eventId;
    private String eventTitle;

    private TextInputLayout tilName;
    private TextInputLayout tilEmail;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private Button btnJoin;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_guest_sign_up, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // ── Args ───────────────────────────────────────────────────────────────
        eventId    = getArguments() != null ? getArguments().getString(ARG_EVENT_ID, "")    : "";
        eventTitle = getArguments() != null ? getArguments().getString(ARG_EVENT_TITLE, "") : "";

        // ── Views ──────────────────────────────────────────────────────────────
        ImageButton btnBack      = view.findViewById(R.id.btn_back);
        TextView    tvEventTitle = view.findViewById(R.id.tv_event_title);
        tilName                  = view.findViewById(R.id.til_name);
        tilEmail                 = view.findViewById(R.id.til_email);
        etName                   = view.findViewById(R.id.et_name);
        etEmail                  = view.findViewById(R.id.et_email);
        btnJoin                  = view.findViewById(R.id.btn_join);
        TextView tvCreateAccount = view.findViewById(R.id.tv_create_account);

        // ── Header ─────────────────────────────────────────────────────────────
        if (tvEventTitle != null && !eventTitle.isEmpty()) {
            tvEventTitle.setText("Join: " + eventTitle);
        }

        // ── Pre-fill email if guest has joined before ──────────────────────────
        String savedEmail = requireContext()
                .getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE)
                .getString(PREF_GUEST_EMAIL, null);
        if (savedEmail != null && etEmail != null) {
            etEmail.setText(savedEmail);
        }

        // ── Back ───────────────────────────────────────────────────────────────
        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    Navigation.findNavController(view).popBackStack());
        }

        // ── Join as guest ──────────────────────────────────────────────────────
        if (btnJoin != null) {
            btnJoin.setOnClickListener(v -> attemptGuestJoin(view));
        }

        // ── Create account link ────────────────────────────────────────────────
        if (tvCreateAccount != null) {
            tvCreateAccount.setOnClickListener(v -> {
                Intent intent = new Intent(requireActivity(), RegisterActivity.class);
                String name  = etName  != null && etName.getText()  != null
                        ? etName.getText().toString().trim()  : "";
                String email = etEmail != null && etEmail.getText() != null
                        ? etEmail.getText().toString().trim() : "";
                if (!name.isEmpty())  intent.putExtra("prefill_name",  name);
                if (!email.isEmpty()) intent.putExtra("prefill_email", email);
                startActivity(intent);
            });
        }
    }

    // ── Validation & submission ───────────────────────────────────────────────

    /**
     * Validates name and email, checks for duplicate registration and capacity,
     * then writes the guest registration to Firestore. On success, persists the
     * guest email to SharedPreferences and pops back to EventDetailsFragment.
     */
    private void attemptGuestJoin(@NonNull View view) {
        boolean valid = true;

        String name  = etName  != null && etName.getText()  != null
                ? etName.getText().toString().trim()  : "";
        String email = etEmail != null && etEmail.getText() != null
                ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            if (tilName != null) tilName.setError("Name is required");
            valid = false;
        } else {
            if (tilName != null) tilName.setError(null);
        }

        if (TextUtils.isEmpty(email)) {
            if (tilEmail != null) tilEmail.setError("Email is required");
            valid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            if (tilEmail != null) tilEmail.setError("Enter a valid email address");
            valid = false;
        } else {
            if (tilEmail != null) tilEmail.setError(null);
        }

        if (!valid) return;

        if (TextUtils.isEmpty(eventId)) {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnJoin.setEnabled(false);
        btnJoin.setText("Joining…");

        String guestKey = emailToKey(email);
        String docId    = guestKey + "_" + eventId;

        FirebaseFirestore db       = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference regRef   =
                db.collection("registrations").document(docId);
        com.google.firebase.firestore.DocumentReference eventRef =
                db.collection("events").document(eventId);

        // ── Duplicate check ────────────────────────────────────────────────────
        regRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                // Already registered — save email locally anyway so the button
                // shows correctly on EventDetailsFragment, then go back.
                saveGuestEmail(email);
                Toast.makeText(requireContext(),
                        "This email is already on the waiting list.",
                        Toast.LENGTH_LONG).show();
                Navigation.findNavController(view).popBackStack();
                return;
            }

            // ── Capacity check ─────────────────────────────────────────────────
            eventRef.get().addOnSuccessListener(eventSnap -> {
                Long count    = eventSnap.getLong("waitlistCount");
                Long capacity = eventSnap.getLong("waitlistCapacity");
                if (count == null) count = 0L;
                if (capacity != null && capacity != -1 && count >= capacity) {
                    Toast.makeText(requireContext(),
                            "This waiting list is full.", Toast.LENGTH_LONG).show();
                    resetJoinButton();
                    return;
                }

                // ── Write registration ─────────────────────────────────────────
                Map<String, Object> registration = new HashMap<>();
                registration.put("guestName",  name);
                registration.put("guestEmail", email);
                registration.put("eventId",    eventId);
                registration.put("status",     "guest_waitlisted");
                registration.put("isGuest",    true);
                registration.put("timestamp",  System.currentTimeMillis());

                regRef.set(registration).addOnSuccessListener(unused -> {
                    // ── Mirror into waitlist subcollection ─────────────────────
                    Map<String, Object> waitlistEntry = new HashMap<>();
                    waitlistEntry.put("guestName",     name);
                    waitlistEntry.put("guestEmail",    email);
                    waitlistEntry.put("eventID",       eventId);
                    waitlistEntry.put("status",        "guest_waitlisted");
                    waitlistEntry.put("isGuest",       true);
                    waitlistEntry.put("joinTime",      Timestamp.now());
                    waitlistEntry.put("lotteryNumber", 0);

                    db.collection("events")
                            .document(eventId)
                            .collection("waitlist")
                            .document(guestKey)
                            .set(waitlistEntry)
                            .addOnSuccessListener(unused2 -> {
                                eventRef.update("waitlistCount", FieldValue.increment(1));

                                // ── Persist email so duplicate check works ─────
                                saveGuestEmail(email);

                                Toast.makeText(requireContext(),
                                        "You've joined the waiting list!",
                                        Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(view).popBackStack();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(requireContext(),
                                        "Something went wrong. Please try again.",
                                        Toast.LENGTH_SHORT).show();
                                resetJoinButton();
                            });
                }).addOnFailureListener(e -> {
                    Toast.makeText(requireContext(),
                            "Something went wrong. Please try again.",
                            Toast.LENGTH_SHORT).show();
                    resetJoinButton();
                });

            }).addOnFailureListener(e -> {
                Toast.makeText(requireContext(),
                        "Something went wrong. Please try again.",
                        Toast.LENGTH_SHORT).show();
                resetJoinButton();
            });

        }).addOnFailureListener(e -> {
            Toast.makeText(requireContext(),
                    "Something went wrong. Please try again.",
                    Toast.LENGTH_SHORT).show();
            resetJoinButton();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Converts an email address to a Firestore-safe document ID segment.
     * Must match the conversion used in EventDetailsFragment.checkGuestWaitlistStatus().
     *
     * @param email raw email address
     * @return sanitised key string
     */
    public static String emailToKey(String email) {
        return email.replace(".", "_").replace("@", "_at_");
    }

    /** Persists the guest email to SharedPreferences. */
    private void saveGuestEmail(String email) {
        requireContext()
                .getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_GUEST_EMAIL, email)
                .apply();
    }

    /** Re-enables the Join button after a failed Firestore operation. */
    private void resetJoinButton() {
        if (btnJoin != null) {
            btnJoin.setEnabled(true);
            btnJoin.setText("Join Waiting List");
        }
    }
}