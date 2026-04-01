/**
 * GuestSignUpFragment.java
 *
 * Role: Shown when a guest user taps "Join Waiting List" on an event they
 * haven't signed in for. Collects a mandatory name and email, then writes a
 * guest registration document to Firestore so the organiser can see interest
 * without requiring a full account.
 */
package com.example.abacus_app;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class GuestSignUpFragment extends Fragment {

    public static final String ARG_EVENT_ID    = "eventId";
    public static final String ARG_EVENT_TITLE = "eventTitle";
    public static final String PREFS_GUEST     = "guest_prefs";
    public static final String PREF_GUEST_EMAIL = "guest_email";

    private String eventId;
    private String eventTitle;

    private TextInputLayout tilName;
    private TextInputLayout tilEmail;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private Button btnJoin;

    private FusedLocationProviderClient fusedLocationClient;
    private boolean isGeoRequired = false;

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

        eventId    = getArguments() != null ? getArguments().getString(ARG_EVENT_ID, "")    : "";
        eventTitle = getArguments() != null ? getArguments().getString(ARG_EVENT_TITLE, "") : "";
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity());

        ImageButton btnBack      = view.findViewById(R.id.btn_back);
        TextView    tvEventTitle = view.findViewById(R.id.tv_event_title);
        tilName                  = view.findViewById(R.id.til_name);
        tilEmail                 = view.findViewById(R.id.til_email);
        etName                   = view.findViewById(R.id.et_name);
        etEmail                  = view.findViewById(R.id.et_email);
        btnJoin                  = view.findViewById(R.id.btn_join);
        TextView tvCreateAccount = view.findViewById(R.id.tv_create_account);

        if (tvEventTitle != null && !eventTitle.isEmpty()) {
            tvEventTitle.setText("Join: " + eventTitle);
        }

        String savedEmail = requireContext()
                .getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE)
                .getString(PREF_GUEST_EMAIL, null);
        if (savedEmail != null && etEmail != null) {
            etEmail.setText(savedEmail);
        }

        if (btnBack != null) {
            btnBack.setOnClickListener(v ->
                    Navigation.findNavController(view).popBackStack());
        }

        if (btnJoin != null) {
            btnJoin.setOnClickListener(v -> attemptGuestJoin(view));
        }

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

        checkIfGeoRequired();
    }

    private void checkIfGeoRequired() {
        if (TextUtils.isEmpty(eventId)) return;
        FirebaseFirestore.getInstance().collection("events").document(eventId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Boolean req = snapshot.getBoolean("geoRequired");
                        isGeoRequired = (req != null && req);
                    }
                });
    }

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

        if (isGeoRequired) {
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            } else {
                fetchLocationAndJoin(name, email, view);
            }
        } else {
            performJoin(name, email, null, view);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            fetchLocationAndJoin(name, email, getView());
        } else if (requestCode == 100) {
            Toast.makeText(getContext(), "Location permission required to join this event.", Toast.LENGTH_LONG).show();
        }
    }

    private void fetchLocationAndJoin(String name, String email, View view) {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getContext(), "Fetching location...", Toast.LENGTH_SHORT).show();
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            performJoin(name, email, location, view);
                        } else {
                            fusedLocationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                performJoin(name, email, lastLoc, view);
                            });
                        }
                    })
                    .addOnFailureListener(e -> performJoin(name, email, null, view));
        }
    }

    private void performJoin(String name, String email, Location location, @NonNull View view) {
        if (TextUtils.isEmpty(eventId)) return;

        btnJoin.setEnabled(false);
        btnJoin.setText("Joining…");

        String guestKey = emailToKey(email);
        String docId    = guestKey + "_" + eventId;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        com.google.firebase.firestore.DocumentReference regRef = db.collection("registrations").document(docId);
        com.google.firebase.firestore.DocumentReference eventRef = db.collection("events").document(eventId);

        regRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                saveGuestEmail(email);
                Toast.makeText(requireContext(), "Already on the waiting list.", Toast.LENGTH_LONG).show();
                Navigation.findNavController(view).popBackStack();
                return;
            }

            eventRef.get().addOnSuccessListener(eventSnap -> {
                Long count = eventSnap.getLong("waitlistCount");
                Long capacity = eventSnap.getLong("waitlistCapacity");
                if (count == null) count = 0L;
                if (capacity != null && capacity != -1 && count >= capacity) {
                    Toast.makeText(requireContext(), "Waiting list is full.", Toast.LENGTH_LONG).show();
                    resetJoinButton();
                    return;
                }

                Map<String, Object> registration = new HashMap<>();
                registration.put("guestName",  name);
                registration.put("guestEmail", email);
                registration.put("eventId",    eventId);
                registration.put("status",     "guest_waitlisted");
                registration.put("isGuest",    true);
                registration.put("timestamp",  System.currentTimeMillis());
                if (location != null) {
                    registration.put("latitude", location.getLatitude());
                    registration.put("longitude", location.getLongitude());
                }

                regRef.set(registration).addOnSuccessListener(unused -> {
                    Map<String, Object> waitlistEntry = new HashMap<>();
                    waitlistEntry.put("guestName",     name);
                    waitlistEntry.put("guestEmail",    email);
                    waitlistEntry.put("eventID",       eventId);
                    waitlistEntry.put("status",        "guest_waitlisted");
                    waitlistEntry.put("isGuest",       true);
                    waitlistEntry.put("joinTime",      Timestamp.now());
                    waitlistEntry.put("lotteryNumber", 0);
                    if (location != null) {
                        waitlistEntry.put("latitude", location.getLatitude());
                        waitlistEntry.put("longitude", location.getLongitude());
                    }

                    db.collection("events").document(eventId).collection("waitlist").document(guestKey)
                            .set(waitlistEntry)
                            .addOnSuccessListener(unused2 -> {
                                eventRef.update("waitlistCount", FieldValue.increment(1));
                                saveGuestEmail(email);
                                Toast.makeText(requireContext(), "You've joined!", Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(view).popBackStack();
                            })
                            .addOnFailureListener(e -> {
                                resetJoinButton();
                            });
                }).addOnFailureListener(e -> resetJoinButton());
            }).addOnFailureListener(e -> resetJoinButton());
        }).addOnFailureListener(e -> resetJoinButton());
    }

    public static String emailToKey(String email) {
        return email.replace(".", "_").replace("@", "_at_");
    }

    private void saveGuestEmail(String email) {
        requireContext()
                .getSharedPreferences(PREFS_GUEST, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_GUEST_EMAIL, email)
                .apply();
    }

    private void resetJoinButton() {
        if (btnJoin != null) {
            btnJoin.setEnabled(true);
            btnJoin.setText("Join Waiting List");
        }
    }
}