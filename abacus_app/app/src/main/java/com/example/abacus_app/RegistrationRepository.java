package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 *  Controller class that manages all actions related to the lottery and waitlist of a all events.
 *  <p>
 *  Responsibilities include updating the statuses of {@link WaitlistEntry}, running the random
 *  lottery selection algorithm, and handling business logic. The lottery works by assigning a
 *  random lottery number to each user as they join the waitlist (the lottery number is secret). At
 *  the time of the draw, the n lowest entries are the winners.
 *  </p>
 *
 *  NOTE: Methods in this class run ASYNCHRONOUSLY and require a callback. Only to be used from UI
 *  classes. For synchronous methods for the architecture layer (repositories), refer to
 *  {@link RegistrationRemoteDataSource}.
 *
 * @author Team Abacus, Kaylee Crocker
 * @version 1.0
 */
public class RegistrationRepository {

    private final RegistrationRemoteDataSource remoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructs the RegistrationRepository object.
     */
    public RegistrationRepository() {
        this.remoteDataSource = new RegistrationRemoteDataSource();
    }

    /**
     * Gets the number of entrants currently on the waitlist, regardless of status.
     *
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     */
    public void getWaitListSize(String eventID, IntegerCallback callback) {
        executor.submit(() -> {
            try {
                Log.d("mytagREPO", "Running from repo...");
                // execute operation
                Integer size = remoteDataSource.getWaitlistSizeSync(eventID);
                Log.d("mytagRDS", "Value (from REPO): " + size);
                mainHandler.post(() -> callback.onResult(size));
                Log.d("mytagREPO", "Results posted...");
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Adds a user to the waitlist of the event. Their lottery number is randomly generated and the
     * timestamp is recorded.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalStateException the waitlist is full or closed
     * @throws IllegalArgumentException the user is already on the waitlist
     */
    public void joinWaitlist(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // logic checks
                /**
                EventRemoteDataSource eventDS = new EventRemoteDataSource();
                boolean waitlistOpen = eventDS.isWaitlistOpen(eventID);
                if (!waitlistOpen) {
                    throw new IllegalStateException("Waitlist is closed.");
                }
                int waitlistCapacity = eventDS.getWaitlistCapacity(eventID);
                if (waitlistCapacity != -1) { // or whatever indicates that a waitlist has no limit <--TODO
                    int waitlistSize = remoteDataSource.getWaitlistSizeSync(eventID);
                    if (waitlistSize >= waitlistCapacity) {
                        throw new IllegalStateException("Waitlist is full.");
                    }
                }
                if (remoteDataSource.isUserOnWaitlistSync(eventID, userID)) {
                    throw new IllegalArgumentException("User is already on waitlist.");
                }
                 **/

                // execute operation
                Random random = new Random();
                WaitlistEntry entry = new WaitlistEntry(
                        userID,
                        eventID,
                        WaitlistEntry.STATUS_WAITLISTED,
                        random.nextInt(100000),
                        Timestamp.now()
                );
                remoteDataSource.joinWaitlistSync(eventID, entry);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Deletes the data related to the waitlist entry of the user.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalArgumentException the given user is not on the waitlist
     */
    public void leaveWaitlist(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // logic checks
                boolean isOnWaitlist = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                if (!isOnWaitlist) {
                    throw new IllegalArgumentException("This user was not on the waitlist.");
                }

                // execute operation
                remoteDataSource.removeWaitlistEntrySync(eventID, userID);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Changes the status of an invited entrant from invited to accepted.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalArgumentException the user is not on the waitlist
     * @throws IllegalStateException the user status is not invited
     */
    public void acceptInvitation(String userID, String eventID, VoidCallback callback) throws Exception {
        executor.submit(() -> {
            try {
                // logic checks
                boolean isOnWaitlist = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                if (!isOnWaitlist) {
                    throw new IllegalArgumentException("This user is not on the waitlist.");
                }
                WaitlistEntry entry = remoteDataSource.getUserWaitlistEntry(userID, eventID);
                if (!entry.getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
                    throw new IllegalStateException("User cannot accept invitation because they were not invited.");
                }

                // execute operation
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_ACCEPTED);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Changes the status of an invited entrant from invited to declined.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalArgumentException the user is not on the waitlist
     * @throws IllegalStateException the user status is not invited
     */
    public void declineInvitation(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // logic checks
                boolean isOnWaitlist = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                if (!isOnWaitlist) {
                    throw new IllegalArgumentException("This user is not on the waitlist.");
                }
                WaitlistEntry entry = remoteDataSource.getUserWaitlistEntry(userID, eventID);
                if (!entry.getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
                    throw new IllegalStateException("User cannot decline invitation because they were not invited.");
                }

                // execute operation
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_DECLINED);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Changes the status of an entrant to cancelled.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalArgumentException the user is not on the waitlist
     * @throws IllegalStateException the user status is not invited
     */
    public void cancelEntrant(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // logic checks
                boolean isOnWaitlist = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                if (!isOnWaitlist) {
                    throw new IllegalArgumentException("This user is not on the waitlist.");
                }

                // execute operation
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_CANCELLED);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    public void runLottery() {

    }

    public void drawReplacement() {

    }

    /**
     * Checks whether or not a user is on the waitlist of an event.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void isOnWaitlist(String userID, String eventID, BooleanCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                boolean result = remoteDataSource.isUserOnWaitlistSync(userID, eventID);
                mainHandler.post(() -> callback.onResult(result));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(false));
            }
        });
    }

    /**
     * Gets the WaitlistEntry associated with a single user for a signle event.
     *
     * @param userID the unique ID of the user in the database
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getUserEntry(String userID, String eventID, EntryCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                WaitlistEntry entry = remoteDataSource.getUserWaitlistEntry(userID, eventID);
                mainHandler.post(() -> callback.onResult(entry));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event, regardless of entry status.
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getAllEntries(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesSync(eventID);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event with status "waitlisted".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getWaitlisted(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_WAITLISTED);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event with status "invited".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getInvited(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_INVITED);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event with status "accepted".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getAccepted(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_ACCEPTED);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event with status "declined".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getDeclined(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_DECLINED);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all entries on the waitlist of a specific event with status "cancelled".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getCancelled(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(eventID, WaitlistEntry.STATUS_CANCELLED);
                mainHandler.post(() -> callback.onResult(waitlist));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Gets all waitlist entries associated with a single user across all events.
     *
     * @param userID the unique ID of the user in the database
     * @param callback callback called when the operation completes
     */
    public void getHistoryForUser(String userID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                // execute logic
                ArrayList<WaitlistEntry> history = remoteDataSource.getHistoryForUserSync(userID);
                mainHandler.post(() -> callback.onResult(history));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Callback interface for method that must return a ArrayList<WaitlistEntry>.
     */
    public interface WaitlistCallback {
        void onResult(ArrayList<WaitlistEntry> waitlist);
    }

    /**
     * Callback interface for method that must return WaitlistEntry object.
     */
    public interface EntryCallback {
        void onResult(WaitlistEntry entry);
    }

    /**
     * Callback interface for method that must return a boolean value.
     */
    public interface BooleanCallback {
        void onResult(Boolean value);
    }

    /**
     * Callback interface for method that must return an Integer value.
     */
    public interface IntegerCallback {
        void onResult(Integer value);
    }

    /**
     * Callback interface for methods that return nothing (void).
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}
