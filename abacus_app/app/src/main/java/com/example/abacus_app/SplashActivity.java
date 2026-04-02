package com.example.abacus_app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Splash Activity
 *
 * Behaviour:
 * - Entry point for the app, shown before the main screen.
 * - Handles user state (first-time vs returning) and navigates accordingly.
 * - Shows an animation and buttons for guest browsing or signing up.
 *
 *
 */
public class SplashActivity extends AppCompatActivity {


    private static final int ANIMATION_DELAY_MS = 1800; //returning user
    private static final int BUTTONS_REVEAL_DELAY_MS = 1200; //first-time user
    private UserRepository userRepository;     // Repository for user data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_activity);

        //Start animation (skip if Reduce Motion is enabled)
        ImageView img = findViewById(R.id.splashAbacus);
        AccessibilityHelper a11y = new AccessibilityHelper(this);
        if (!a11y.isReduceMotion()) {
            AnimatedVectorDrawableCompat avd = AnimatedVectorDrawableCompat.create(this, R.drawable.ic_abacus_animated);
            if (avd != null) {
                img.setImageDrawable(avd);
                avd.start();
            }
        }

        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        TextView tvBrowseGuest = findViewById(R.id.tvBrowseGuest);

        // Hide buttons initially regardless of user state
        btnGetStarted.setVisibility(View.INVISIBLE);
        tvBrowseGuest.setVisibility(View.INVISIBLE);

        // Wire up buttons immediately (invisible until revealed)
        btnGetStarted.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
        tvBrowseGuest.setOnClickListener(v -> goToMain(true, "entrant"));

        // Build UserLocalDataSource using Kotlin extension
        UserLocalDataSource  localDataSource  = new UserLocalDataSource(getApplicationContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getCurrentUserIdAsync(uuid -> {
            if (uuid == null || uuid.isEmpty()) {
                // Brand new device — no UUID yet, show onboarding buttons
                showButtons(btnGetStarted, tvBrowseGuest);
                return;
            }
            userRepository.getProfileAsync(user -> {
                // Only navigate as a signed-in user if Firebase Auth still holds
                // a valid session. If Auth has expired the user must log in again.
                FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                boolean firebaseSignedIn = (firebaseUser != null && !firebaseUser.isAnonymous());

                // If getProfileAsync returned null, it means either the user doesn't exist
                // OR UserRepository already detected isDeleted=true and signed them out.
                if (user == null && firebaseSignedIn) {
                    // This covers the "Deleted" case. User exists in Auth but Repository returned null.
                    showButtons(btnGetStarted, tvBrowseGuest);
                    Toast.makeText(this, "Account is no longer active.", Toast.LENGTH_LONG).show();
                    return;
                }

                if (firebaseSignedIn && user != null
                        && user.getLastLoginAt() != null && !user.getLastLoginAt().isEmpty()) {
                    // Returning logged-in user — go straight to main
                    String role = (user.getRole() != null && !user.getRole().isEmpty())
                            ? user.getRole() : "entrant";
                    long delay = a11y.isReduceMotion() ? 0 : ANIMATION_DELAY_MS;
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> goToMain(false, role),
                            delay
                    );
                } else {
                    // UUID exists but no active Firebase session (guest or expired).
                    // Auto-navigate as guest — they can log in from the profile screen.
                    long delay = a11y.isReduceMotion() ? 0 : ANIMATION_DELAY_MS;
                    new Handler(Looper.getMainLooper()).postDelayed(
                            () -> goToMain(true, "entrant"),
                            delay
                    );
                }
            });
        });
    }

    /**
     * Reveals the onboarding buttons with a fade-in after the animation delay.
     * Only shown to brand new devices with no UUID at all.
     */
    private void showButtons(Button btnGetStarted, TextView tvBrowseGuest) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            btnGetStarted.setVisibility(View.VISIBLE);
            tvBrowseGuest.setVisibility(View.VISIBLE);

            btnGetStarted.setAlpha(0f);
            tvBrowseGuest.setAlpha(0f);
            btnGetStarted.animate().alpha(1f).setDuration(400).start();
            tvBrowseGuest.animate().alpha(1f).setDuration(400).start();
        }, BUTTONS_REVEAL_DELAY_MS);
    }

    /**
     * Navigates to MainActivity, passing whether the user is a guest and their role.
     * Clears the back stack so the user cannot navigate back to splash.
     *
     * @param isGuest  true if browsing without signing in.
     * @param userRole the user's role ("entrant", "organizer", or "admin").
     */
    private void goToMain(boolean isGuest, String userRole) {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.putExtra("userRole", userRole);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
