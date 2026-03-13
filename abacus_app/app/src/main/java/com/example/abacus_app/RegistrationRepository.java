package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * Controller class that manages all actions related to the lottery and waitlist.
 * Joins 'registrations' data with 'users' data for UI display.
 * 
 * @author Himesh
 * @version 1.0
 */
public class RegistrationRepository {

    private final RegistrationRemoteDataSource remoteDataSource;
    private final UserRemoteDataSource userRemoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Initializes the repository and its data sources.
     */
    public RegistrationRepository() {
        this.remoteDataSource = new RegistrationRemoteDataSource();
        this.userRemoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
    }

    /**
     * Populates user details (name, email) into waitlist entries by fetching from the users collection.
     */
    private void populateUserInfo(ArrayList<WaitlistEntry> waitlist) {
        for (WaitlistEntry entry : waitlist) {
            try {
                User user = userRemoteDataSource.getUserSync(entry.getUserId());
                if (user != null) {
                    entry.setUserName(user.getName());
                    entry.setUserEmail(user.getEmail());
                }
            } catch (Exception e) {
                Log.e("RegistrationRepository", "Failed to fetch user info for " + entry.getUserId(), e);
            }
        }
    }

    /**
     * Gets the total number of people on the waitlist for a specific event.
     */
    public void getWaitListSize(String eventID, IntegerCallback callback) {
        executor.submit(() -> {
            try {
                Integer size = remoteDataSource.getWaitlistSizeSync(eventID);
                mainHandler.post(() -> callback.onResult(size));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Adds a user to the waitlist for an event.
     */
    public void joinWaitlist(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                Random random = new Random();
                WaitlistEntry entry = new WaitlistEntry(
                        userID,
                        eventID,
                        WaitlistEntry.STATUS_WAITLISTED,
                        random.nextInt(100000),
                        System.currentTimeMillis()
                );
                remoteDataSource.joinWaitlistSync(eventID, entry);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Removes a user from an event's waitlist.
     */
    public void leaveWaitlist(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                remoteDataSource.removeWaitlistEntrySync(eventID, userID);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Updates an entrant's status to 'accepted'.
     */
    public void acceptInvitation(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_ACCEPTED);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Updates an entrant's status to 'declined'.
     */
    public void declineInvitation(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_DECLINED);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Updates an entrant's status to 'cancelled'.
     * US 02.06.04 implementation.
     */
    public void cancelEntrant(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_CANCELLED);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Checks if a user is currently on the waitlist for an event.
     */
    public void isOnWaitlist(String userID, String eventID, BooleanCallback callback) {
        executor.submit(() -> {
            try {
                boolean result = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                mainHandler.post(() -> callback.onResult(result));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(false));
            }
        });
    }

    /**
     * Fetches a specific waitlist entry with user details populated.
     */
    public void getUserEntry(String userID, String eventID, EntryCallback callback) {
        executor.submit(() -> {
            try {
                WaitlistEntry entry = remoteDataSource.getUserWaitlistEntry(userID, eventID);
                if (entry != null) {
                    User user = userRemoteDataSource.getUserSync(entry.getUserId());
                    if (user != null) {
                        entry.setUserName(user.getName());
                        entry.setUserEmail(user.getEmail());
                    }
                }
                mainHandler.post(() -> callback.onResult(entry));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Fetches all waitlist entries for an event, including user details.
     * US 02.02.01 implementation.
     */
    public void getAllEntries(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesSync(eventID);
                populateUserInfo(waitlist);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Fetches only those entries currently in 'waitlisted' status.
     */
    public void getWaitlisted(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_WAITLISTED);
                populateUserInfo(waitlist);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Fetches registration history for a specific user.
     */
    public void getHistoryForUser(String userID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> history = remoteDataSource.getHistoryForUserSync(userID);
                mainHandler.post(() -> callback.onResult(history));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    public interface WaitlistCallback { void onResult(ArrayList<WaitlistEntry> waitlist); }
    public interface EntryCallback    { void onResult(WaitlistEntry entry); }
    public interface BooleanCallback  { void onResult(Boolean value); }
    public interface IntegerCallback  { void onResult(Integer value); }
    public interface VoidCallback     { void onComplete(Exception error); }
}
