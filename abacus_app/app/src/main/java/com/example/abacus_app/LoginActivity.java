package com.example.abacus_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/**
 * Activity for user login forexisting users.using Firebase Auth.
 *
 * Responsibilities:
 * - Handles user login using Firebase Auth.
 * - Links Firebase Auth user data to Firestore profile.
 * - Syncs display name between Firebase Auth and Firestore.
 * - Handles SSO (Single Sign-On) logic.
 * - Handles password reset.
 * - Handles registration flow.
 *
 * When a user logs in, their profile shows the correct email and display name,
 * and is marked as a non-guest user.
 *
 */
public class LoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private UserRepository userRepository;
    private UserLocalDataSource localDataSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loginpage);

        mAuth = FirebaseAuth.getInstance();

        localDataSource = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn    = findViewById(R.id.btnSignIn);
        TextView tvForgot   = findViewById(R.id.tvForgot);
        TextView tvSignUp   = findViewById(R.id.tvSignUp);

        btnSignIn.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Please enter your password", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            btnSignIn.setEnabled(false);
            btnSignIn.setText("Signing in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            restoreIdentityThenNavigate(user);
                        }
                    })
                    .addOnFailureListener(e -> {
                        btnSignIn.setEnabled(true);
                        btnSignIn.setText("Sign In");

                        String errorMessage = "Login failed";
                        if (e.getMessage() != null) {
                            if (e.getMessage().contains("invalid-email")) {
                                errorMessage = "Invalid email format";
                            } else if (e.getMessage().contains("wrong-password")) {
                                errorMessage = "Incorrect password";
                            } else if (e.getMessage().contains("user-not-found")) {
                                errorMessage = "No account found with this email";
                            } else if (e.getMessage().contains("user-disabled")) {
                                errorMessage = "This account has been disabled";
                            } else if (e.getMessage().contains("too-many-requests")) {
                                errorMessage = "Too many failed attempts. Please try again later";
                            } else {
                                errorMessage = e.getMessage();
                            }
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                    });
        });

        tvForgot.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                Toast.makeText(this, "Enter your email first", Toast.LENGTH_SHORT).show();
                return;
            }
            mAuth.sendPasswordResetEmail(email)
                    .addOnSuccessListener(unused ->
                            Toast.makeText(this, "Reset email sent!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        tvSignUp.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    /**
     * Reads the user's role from Firestore using the local UUID (not Firebase Auth UID)
     * then navigates to MainActivity with the role as an intent extra.
     */
    private void fetchRoleAndNavigate() {
        String uuid = localDataSource.getUUIDSync();

        if (uuid == null) {
            navigateToMain("entrant");
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(uuid)
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null) role = "entrant";
                    android.util.Log.d("LoginActivity", "Role from Firestore: " + role + " for UUID: " + uuid);
                    navigateToMain(role);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("LoginActivity", "Failed to read role", e);
                    navigateToMain("entrant");
                });
    }

    private void navigateToMain(String role) {
        getSharedPreferences("abacus_prefs", MODE_PRIVATE)
                .edit()
                .putBoolean("has_launched_before", true)
                .apply();

        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isGuest", false);
        intent.putExtra("role", role);
        startActivity(intent);
        finish();
    }

    /**
     * Looks up the user's existing Firestore document by email, restores their
     * UUID to SharedPreferences (so history is preserved), updates profile fields,
     * then navigates to MainActivity. Everything is chained so navigation only
     * happens after the UUID is fully restored.
     */
    private void restoreIdentityThenNavigate(FirebaseUser authUser) {
        if (authUser == null || authUser.getEmail() == null) {
            fetchRoleAndNavigate();
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", authUser.getEmail())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        // Restore the UUID that owns this account's history
                        String existingUuid = querySnapshot.getDocuments().get(0).getId();
                        localDataSource.saveDeviceId(existingUuid);
                        android.util.Log.d("LoginActivity",
                                "Restored UUID: " + existingUuid + " for " + authUser.getEmail());
                    }
                    updateProfileFields(authUser);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("LoginActivity", "UUID lookup failed, proceeding anyway", e);
                    updateProfileFields(authUser);
                });
    }

    private void updateProfileFields(FirebaseUser authUser) {
        Map<String, Object> updates = new HashMap<>();
        if (authUser.getEmail() != null) updates.put("email", authUser.getEmail());
        if (authUser.getDisplayName() != null && !authUser.getDisplayName().isEmpty()) {
            updates.put("name", authUser.getDisplayName());
        }
        updates.put("isGuest", false);
        updates.put("lastLoginAt", System.currentTimeMillis());

        userRepository.saveProfileAsync(updates, error -> {
            if (error != null) {
                android.util.Log.e("LoginActivity", "Failed to update profile: " + error.getMessage());
            } else if (authUser.getDisplayName() == null || authUser.getDisplayName().isEmpty()) {
                syncNameFromFirestoreToAuth(authUser);
            }
            fetchRoleAndNavigate();
        });
    }

    private void syncNameFromFirestoreToAuth(FirebaseUser authUser) {
        userRepository.getProfileAsync(user -> {
            if (user != null && user.getName() != null && !user.getName().isEmpty()
                    && !"New User".equals(user.getName())) {
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(user.getName())
                        .build();
                authUser.updateProfile(profileUpdates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                android.util.Log.d("LoginActivity", "Updated Firebase Auth display name");
                            }
                        });
            }
        });
    }
}