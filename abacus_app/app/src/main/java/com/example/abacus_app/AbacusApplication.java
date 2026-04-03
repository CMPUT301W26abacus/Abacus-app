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
 * Application class for initializing global components and repositories.
 * This class is instantiated when the app starts and remains active for the entire app lifecycle.
 */
public class AbacusApplication extends Application {

    private static final String TAG = "AbacusApplication";
    
    private UserRepository userRepository;

    @Override
    protected void attachBaseContext(Context base) {
        AccessibilityHelper helper = new AccessibilityHelper(base);
        Configuration config = AccessibilityHelper.buildConfig(base, helper.getTextScale());
        super.attachBaseContext(base.createConfigurationContext(config));
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