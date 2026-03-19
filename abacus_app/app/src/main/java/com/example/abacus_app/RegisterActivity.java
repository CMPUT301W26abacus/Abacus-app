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

public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private UserRepository userRepository;
    private Button btnRegister; // Make btnRegister a class field

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registerpage);

        mAuth = FirebaseAuth.getInstance();

        // Initialize UserRepository
        UserLocalDataSource localDataSource = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);

        EditText etName     = findViewById(R.id.etName);
        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        btnRegister = findViewById(R.id.btnRegister); // Assign to class field, not declare new variable
        TextView tvLogin    = findViewById(R.id.tvLogin);
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);

        // Back button goes to previous page
        btnBack.setOnClickListener(v -> finish());

        btnRegister.setOnClickListener(v -> {
            String name     = etName.getText().toString().trim();
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            // Basic validation
            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable button during registration
            btnRegister.setEnabled(false);
            btnRegister.setText("Creating account...");

            // Create user in Firebase Auth
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        // Update Firebase Auth profile with display name
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();

                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(task -> {
                                        // Whether profile update succeeds or fails, continue with Firestore update
                                        saveToFirestore(name, email);
                                    });
                        } else {
                            // If no user, still save to Firestore
                            saveToFirestore(name, email);
                        }
                    })
                    .addOnFailureListener(e -> {
                        btnRegister.setEnabled(true);
                        btnRegister.setText("Register");
                        
                        String errorMessage = "Registration failed";
                        if (e.getMessage() != null) {
                            if (e.getMessage().contains("email-already-in-use")) {
                                errorMessage = "An account with this email already exists";
                            } else if (e.getMessage().contains("invalid-email")) {
                                errorMessage = "Invalid email format";
                            } else if (e.getMessage().contains("weak-password")) {
                                errorMessage = "Password is too weak";
                            } else {
                                errorMessage = e.getMessage();
                            }
                        }
                        
                        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show();
                        android.util.Log.w("RegisterActivity", "Registration failed", e);
        });

        // Go back to login
            tvLogin.setOnClickListener(view -> {
                startActivity(new Intent(this, LoginActivity.class));
                // Note: Do NOT call finish() here to preserve back stack
            });
        });
    }

    /**
     * Saves the user profile to Firestore after Firebase Auth account creation.
     */
    private void saveToFirestore(String name, String email) {
        String createdAt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(new Date());
        
        String lastLoginAt = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault()
        ).format(new Date());

        Map<String, Object> updates = new HashMap<>();
        updates.put("name",        name);
        updates.put("email",       email);
        updates.put("createdAt",   createdAt);
        updates.put("lastLoginAt", lastLoginAt);
        updates.put("isGuest",     false);
        updates.put("isDeleted",   false);

        userRepository.saveProfileAsync(updates, error -> {
            btnRegister.setEnabled(true);
            btnRegister.setText("Register");
            
            if (error == null) {
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                goToMain();
            } else {
                Toast.makeText(this,
                        "Error saving profile: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isGuest", false);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
