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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_loginpage);

        mAuth = FirebaseAuth.getInstance();

        // Initialize UserRepository
        UserLocalDataSource localDataSource = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button btnSignIn   = findViewById(R.id.btnSignIn);
        TextView tvForgot   = findViewById(R.id.tvForgot);
        TextView tvSignUp   = findViewById(R.id.tvSignUp);
        Button  btnSSO      = findViewById(R.id.btnSSO);

        btnSignIn.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Validate inputs
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

            // Disable button during login attempt
            btnSignIn.setEnabled(false);
            btnSignIn.setText("Signing in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        // Update the Firestore profile with Firebase Auth data
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            linkAuthUserToProfile(user);
                        }
                        
                        getSharedPreferences("abacus_prefs", MODE_PRIVATE)
                                .edit()
                                .putBoolean("has_launched_before", true)
                                .apply();

                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        
                        // Navigate to MainActivity with isGuest=false
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.putExtra("isGuest", false);
                        startActivity(intent);
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        // Re-enable button on failure
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
                        android.util.Log.w("LoginActivity", "Login failed", e);
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

        tvSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        btnSSO.setOnClickListener(v -> {
            // TODO: Add SSO logic
            Toast.makeText(this, "SSO tapped", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Links the Firebase Auth user data to the existing Firestore profile document.
     * This ensures that when a user logs in, their profile shows the correct email
     * and display name, and is marked as a non-guest user.
     * Also syncs display name between Firebase Auth and Firestore.
     */
    private void linkAuthUserToProfile(FirebaseUser authUser) {
        if (authUser == null) return;
        
        Map<String, Object> updates = new HashMap<>();
        
        // Update email from Firebase Auth
        if (authUser.getEmail() != null) {
            updates.put("email", authUser.getEmail());
        }
        
        // Update display name from Firebase Auth if available
        if (authUser.getDisplayName() != null && !authUser.getDisplayName().isEmpty()) {
            updates.put("name", authUser.getDisplayName());
        }
        
        // Mark as non-guest
        updates.put("isGuest", false);
        updates.put("lastLoginAt", System.currentTimeMillis());

        android.util.Log.d("LoginActivity", "Linking profile with email: " + authUser.getEmail() + 
            ", displayName: " + authUser.getDisplayName());

        userRepository.saveProfileAsync(updates, error -> {
            if (error != null) {
                // Log error but don't interrupt the login flow
                android.util.Log.e("LoginActivity", "Failed to update profile: " + error.getMessage());
            } else {
                android.util.Log.d("LoginActivity", "Profile successfully linked");
                
                // If Firebase Auth doesn't have display name but Firestore might, 
                // try to sync it back to Firebase Auth
                if ((authUser.getDisplayName() == null || authUser.getDisplayName().isEmpty())) {
                    syncNameFromFirestoreToAuth(authUser);
                }
            }
        });
    }

    /**
     * If Firebase Auth doesn't have a display name but Firestore does,
     * update Firebase Auth with the name from Firestore.
     */
    private void syncNameFromFirestoreToAuth(FirebaseUser authUser) {
        userRepository.getProfileAsync(user -> {
            if (user != null && user.getName() != null && !user.getName().isEmpty() && 
                !"New User".equals(user.getName())) {
                
                // Update Firebase Auth with the name from Firestore
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(user.getName())
                        .build();

                authUser.updateProfile(profileUpdates)
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful()) {
                                android.util.Log.d("LoginActivity", "Updated Firebase Auth display name: " + user.getName());
                            } else {
                                android.util.Log.w("LoginActivity", "Failed to update Firebase Auth display name", task.getException());
                            }
                        });
            }
        });
    }
}
