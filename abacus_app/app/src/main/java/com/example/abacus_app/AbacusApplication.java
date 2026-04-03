package com.example.abacus_app;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

/**
 * AbacusApplication - App initialization and global setup
 *
 * Runs once when the app starts and stays active the entire time.
 *
 * What it does:
 * - Initializes Firebase Firestore with offline support enabled
 * - Creates repositories for accessing user data
 * - Checks if user is already logged in
 *
 * Theme Switching:
 * - Does NOT wrap base context (keeps it responsive to system theme changes)
 * - Font scale applied per-activity in onCreate() instead of globally
 * - This allows instant dark/light mode switching without requiring restart
 *
 * @author Dyna
 */
public class AbacusApplication extends Application {

    private static final String TAG = "AbacusApplication";
    
    private UserRepository userRepository;

    @Override
    protected void attachBaseContext(Context base) {
        // Don't wrap context — keep it responsive to system configuration changes
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        Log.d(TAG, "AbacusApplication starting...");
        
        // Initialize Firebase Firestore settings
        initializeFirestore();
        
        // Initialize repositories
        initializeRepositories();
        
        // Check if user is already authenticated
        checkAuthenticationStatus();
        
        Log.d(TAG, "AbacusApplication initialization complete");
    }

    /**
     * Initialize Firebase Firestore with optimal settings
     */
    private void initializeFirestore() {
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)  // Enable offline persistence
                .build();
        FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        Log.d(TAG, "Firestore initialized with persistence enabled");
    }

    /**
     * Initialize global repositories
     */
    private void initializeRepositories() {
        // Initialize UserRepository with dependencies
        UserLocalDataSource localDataSource = new UserLocalDataSource(this);
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        userRepository = new UserRepository(localDataSource, remoteDataSource);
        
        Log.d(TAG, "Repositories initialized");
    }

    /**
     * Check current authentication status and log it
     */
    private void checkAuthenticationStatus() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            Log.d(TAG, "User already authenticated (anonymous=" + currentUser.isAnonymous() + ")");
        } else {
            Log.d(TAG, "No authenticated user found");
        }
    }

    /**
     * Get the global UserRepository instance
     * @return UserRepository instance
     */
    public UserRepository getUserRepository() {
        return userRepository;
    }
}