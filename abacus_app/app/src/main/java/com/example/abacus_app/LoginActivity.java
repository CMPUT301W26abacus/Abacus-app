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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Activity for user login for existing users, using Firebase Auth.
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
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);

        // Back button goes to previous page
        btnBack.setOnClickListener(v -> finish());

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

                        String errorMessage = "Login failed. Please try again.";
                        if (e instanceof com.google.firebase.FirebaseNetworkException
                                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("network"))) {
                            errorMessage = "No internet connection. Please check your network and try again.";
                        } else if (e.getMessage() != null) {
                            if (e.getMessage().contains("invalid-email")) {
                                errorMessage = "Invalid email format";
                            } else if (e.getMessage().contains("wrong-password")
                                    || e.getMessage().contains("invalid-credential")) {
                                errorMessage = "Incorrect email or password";
                            } else if (e.getMessage().contains("user-not-found")) {
                                errorMessage = "No account found with this email";
                            } else if (e.getMessage().contains("user-disabled")) {
                                errorMessage = "This account has been disabled";
                            } else if (e.getMessage().contains("too-many-requests")) {
                                errorMessage = "Too many failed attempts. Please try again later";
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
                // Note: Do NOT call finish() here to preserve back stack
    }

    /**
     * Reads the user's role from Firestore if not already provided.
     * If existingRole is provided (from the email lookup), uses that.
     * Otherwise reads from users/{firebaseUID}.
     */
    private void fetchRoleAndNavigate(String existingRole) {
        // If we already have the role from the email lookup, use it
        if (existingRole != null && !existingRole.isEmpty()) {
            android.util.Log.d("LoginActivity", "Using existing role: " + existingRole);
            navigateToMain(existingRole);
            return;
        }

        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        if (firebaseUser == null) {
            navigateToMain("entrant");
            return;
        }

        String firebaseUid = firebaseUser.getUid();
        FirebaseFirestore.getInstance()
                .collection("users")
                .document(firebaseUid)
                .get()
                .addOnSuccessListener(doc -> {
                    String role = doc.getString("role");
                    if (role == null) role = "entrant";
                    android.util.Log.d("LoginActivity", "Role from Firestore: " + role);
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
        intent.putExtra("userRole", role);
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
            updateProfileFields(authUser, null);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("email", authUser.getEmail())
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    String existingRole = null;
                    if (!querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);

                        // Check if the account is inactivated/deleted
                        Boolean isDeleted = doc.getBoolean("isDeleted");
                        if (Boolean.TRUE.equals(isDeleted)) {
                            // Sign out of Firebase Auth immediately
                            mAuth.signOut();

                            // Reset the Sign In button so they can try again with a different account
                            Button btnSignIn = findViewById(R.id.btnSignIn);
                            btnSignIn.setEnabled(true);
                            btnSignIn.setText("Sign In");

                            // Explicitly inform the user
                            Toast.makeText(this, "Your account has been inactivated.", Toast.LENGTH_LONG).show();
                            return; // Stop the navigation chain here
                        }
                        // Restore the UUID that owns this account's history
                        String existingUuid = doc.getId();
                        localDataSource.saveDeviceId(existingUuid);
                        android.util.Log.d("LoginActivity", "Identity restored from existing account");

                        // Preserve the existing role from the document
                        existingRole = doc.getString("role");
                    }
                    updateProfileFields(authUser, existingRole);
                })
                .addOnFailureListener(e -> {
                    android.util.Log.e("LoginActivity", "UUID lookup failed, proceeding anyway", e);
                    updateProfileFields(authUser, null);
                });
    }

    private void updateProfileFields(FirebaseUser authUser, String existingRole) {
        String lastLoginAt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        Map<String, Object> updates = new HashMap<>();
        if (authUser.getEmail() != null) updates.put("email", authUser.getEmail());
        if (authUser.getDisplayName() != null && !authUser.getDisplayName().isEmpty()) {
            updates.put("name", authUser.getDisplayName());
        }
        updates.put("isGuest", false);
        updates.put("lastLoginAt", lastLoginAt);

        android.util.Log.d("LoginActivity", "Saving profile updates");

        userRepository.saveProfileAsync(updates, error -> {
            if (error != null) {
                android.util.Log.e("LoginActivity", "Failed to update profile: " + error.getMessage());
            } else {
                android.util.Log.d("LoginActivity", "Profile updated successfully");
                if (authUser.getDisplayName() == null || authUser.getDisplayName().isEmpty()) {
                    syncNameFromFirestoreToAuth(authUser);
                }
            }
            fetchRoleAndNavigate(existingRole);
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