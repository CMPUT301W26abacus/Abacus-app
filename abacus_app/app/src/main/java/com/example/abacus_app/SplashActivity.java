package com.example.abacus_app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.Dispatchers;

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

    // How long to show the animation before auto-navigating (returning user)
    private static final int ANIMATION_DELAY_MS = 1800;

    // How long to show the animation before revealing buttons (first-time user)
    private static final int BUTTONS_REVEAL_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.splash_activity);

        //Start animation
        ImageView img = findViewById(R.id.splashAbacus);
        AnimatedVectorDrawableCompat avd = AnimatedVectorDrawableCompat.create(this, R.drawable.ic_abacus_animated);
        if (avd != null) {
            img.setImageDrawable(avd);
            avd.start();
        }

        Button btnGetStarted = findViewById(R.id.btnGetStarted);
        TextView tvBrowseGuest = findViewById(R.id.tvBrowseGuest);

        // Hide buttons initially regardless of user state
        btnGetStarted.setVisibility(View.INVISIBLE);
        tvBrowseGuest.setVisibility(View.INVISIBLE);

        // Build the data source to read UUID from DataStore
        UserLocalDataSource localDataSource = new UserLocalDataSource(
                DataStoreHelperKt.getDataStore(getApplicationContext())
        );

        // Read UUID on a background thread, then decide on the main thread
        new Thread(() -> {
            String uuid = null;
            try {
                // Call the Kotlin suspend function synchronously from Java
                uuid = BuildersKt.runBlocking(
                        Dispatchers.getIO(),
                        (scope, continuation) -> localDataSource.getUUID(continuation)
                );
            } catch (Exception e) {
                e.printStackTrace();
            }

            final boolean isReturningUser = (uuid != null && !uuid.isEmpty());

            // Switch back to main thread to update UI
            new Handler(Looper.getMainLooper()).post(() ->
                    handleUserState(isReturningUser, btnGetStarted, tvBrowseGuest));

        }).start();
    }

    /**
     * Decides what to show based on whether a UUID was found in DataStore.
     *
     * @param isReturningUser true if a UUID already exists on this device.
     * @param btnGetStarted   the "Get Started" button.
     * @param tvBrowseGuest   the "Browse as guest" text view.
     */
    private void handleUserState(boolean isReturningUser, Button btnGetStarted, TextView tvBrowseGuest) {
        if (isReturningUser) {
            // UUID recognised → go straight to MainActivity after animation
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                goToMain(currentUser == null); // guest only if Firebase lost the session
            }, ANIMATION_DELAY_MS);

        } else {
            // No UUID → new user, show onboarding buttons after animation
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                btnGetStarted.setVisibility(View.VISIBLE);
                tvBrowseGuest.setVisibility(View.VISIBLE);

                btnGetStarted.setAlpha(0f);
                tvBrowseGuest.setAlpha(0f);
                btnGetStarted.animate().alpha(1f).setDuration(400).start();
                tvBrowseGuest.animate().alpha(1f).setDuration(400).start();
            }, BUTTONS_REVEAL_DELAY_MS);

            // "Get Started" → Login screen
            btnGetStarted.setOnClickListener(v -> {
                startActivity(new Intent(SplashActivity.this, LoginActivity.class));
                finish();
            });

            // "Browse as guest" → MainActivity without authentication
            tvBrowseGuest.setOnClickListener(v -> goToMain(true));
        }
    }

    /**
     * Navigates to MainActivity, passing whether the user is a guest.
     * Clears the back stack so the user cannot navigate back to splash.
     *
     * @param isGuest true if browsing without signing in.
     */
    private void goToMain(boolean isGuest) {
        Intent intent = new Intent(SplashActivity.this, MainActivity.class);
        intent.putExtra("isGuest", isGuest);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
