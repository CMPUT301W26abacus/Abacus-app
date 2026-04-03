package com.example.abacus_app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
 * RegisterActivity - Handles new user registration
 *
 * Creates new user accounts in Firebase Auth and Firestore.
 *
 * What it does:
 * - Takes name, email, password, and role (entrant/organizer)
 * - If organizer, also takes organization name
 * - Validates email format and password strength
 * - Creates Firebase Auth account
 * - Saves user profile to Firestore with role and timestamps
 * - Shows error messages for duplicate emails, weak passwords, etc
 * - Links back to login for existing users
 *
 * Theme Switching:
 * - onResume() detects dark mode changes and recreates activity with new colors
 *
 * @author Dyna
 */
public class RegisterActivity extends AppCompatActivity {
    private FirebaseAuth mAuth;
    private UserRepository userRepository;
    private Button btnRegister;
    private RadioButton rbOrganizer;
    private EditText etOrganizationName;
    private TextView labelOrgName;
    private int lastNightMode = -1;  // Track theme changes for auto-recreation

    @Override
    protected void attachBaseContext(android.content.Context base) {
        AccessibilityHelper a11y = new AccessibilityHelper(base);
        android.content.res.Configuration config = AccessibilityHelper.buildConfig(base, a11y.getTextScale());
        super.attachBaseContext(base.createConfigurationContext(config));
    }

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
        btnRegister = findViewById(R.id.btnRegister);
        TextView tvLogin    = findViewById(R.id.tvLogin);
        android.widget.ImageButton btnBack = findViewById(R.id.btnBack);
        RadioGroup rgRole       = findViewById(R.id.rgRole);
        rbOrganizer             = findViewById(R.id.rbOrganizer);
        etOrganizationName      = findViewById(R.id.etOrganizationName);
        labelOrgName            = findViewById(R.id.labelOrgName);

        // Show/hide organization name field based on role selection
        rgRole.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isOrganizer = (checkedId == R.id.rbOrganizer);
            int visibility = isOrganizer ? View.VISIBLE : View.GONE;
            labelOrgName.setVisibility(visibility);
            etOrganizationName.setVisibility(visibility);
        });

        // Back button goes to previous page
        btnBack.setOnClickListener(v -> finish());

        // Go back to login
        tvLogin.setOnClickListener(view ->
                startActivity(new Intent(this, LoginActivity.class)));

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

            String selectedRole = rbOrganizer.isChecked() ? "organizer" : "entrant";
            String orgName      = etOrganizationName.getText().toString().trim();

            // Create user in Firebase Auth
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = authResult.getUser();
                        if (user != null) {
                            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                                    .setDisplayName(name)
                                    .build();
                            user.updateProfile(profileUpdates)
                                    .addOnCompleteListener(task ->
                                            saveToFirestore(name, email, selectedRole, orgName));
                        } else {
                            saveToFirestore(name, email, selectedRole, orgName);
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
        });
    }

    /**
     * Saves the user profile to Firestore after Firebase Auth account creation.
     */
    private void saveToFirestore(String name, String email, String role, String orgName) {
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
        updates.put("role",        role);
        if ("organizer".equals(role) && !orgName.isEmpty()) {
            updates.put("organizationName", orgName);
        }

        userRepository.saveProfileAsync(updates, error -> {
            btnRegister.setEnabled(true);
            btnRegister.setText("Register");

            if (error == null) {
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                goToMain(role);
            } else {
                Toast.makeText(this,
                        "Error saving profile: " + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToMain(String role) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("isGuest", false);
        intent.putExtra("userRole", role);
        intent.putExtra("role", role);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Detect theme changes and trigger activity recreation
        int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (lastNightMode != -1 && lastNightMode != currentNightMode) {
            // Theme changed, recreate the activity to apply new theme colors
            new android.os.Handler(android.os.Looper.getMainLooper()).post(this::recreate);
        }
        lastNightMode = currentNightMode;
    }
}
