package com.example.abacus_app;

import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller class that manages all actions related to the lottery and waitlist.
 * Joins 'registrations' data with 'users' data for UI display.
 * <p>
 *  Responsibilities include updating the statuses of {@link WaitlistEntry}, running the random
 *  lottery selection algorithm, and handling business logic. The lottery works by assigning a
 *  random lottery number to each user as they join the waitlist (the lottery number is secret). At
 *  the time of the draw, the n lowest entries are the winners.
 *
 *  NOTE: Methods in this class run ASYNCHRONOUSLY and require a callback. Only to be used from UI
 *  classes. For synchronous methods for the architecture layer (repositories), refer to
 *  {@link RegistrationRemoteDataSource}.
 *  </p>
 *
 * @author Kaylee
 */
public class RegistrationRepository {

    private final RegistrationRemoteDataSource remoteDataSource;
    private final UserRemoteDataSource         userRemoteDataSource;
    /** Separated into a field so tests can inject a mock EventRemoteDataSource. */
    private final EventRemoteDataSource        eventRemoteDataSource;
    private final ExecutorService              executor    = Executors.newSingleThreadExecutor();
    private final Handler                      mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructs the RegistrationRepository object (production use).
     */
    public RegistrationRepository() {
        this.remoteDataSource      = new RegistrationRemoteDataSource();
        this.userRemoteDataSource  = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        this.eventRemoteDataSource = new EventRemoteDataSource();
    }

    /**
     * Constructs the RegistrationRepository with injected data sources.
     * Intended for unit tests — allows mocking Firestore dependencies.
     *
     * @param remoteDataSource      mocked or real waitlist data source
     * @param userRemoteDataSource  mocked or real user data source
     * @param eventRemoteDataSource mocked or real event data source
     */
    RegistrationRepository(RegistrationRemoteDataSource remoteDataSource,
                           UserRemoteDataSource userRemoteDataSource,
                           EventRemoteDataSource eventRemoteDataSource) {
        this.remoteDataSource      = remoteDataSource;
        this.userRemoteDataSource  = userRemoteDataSource;
        this.eventRemoteDataSource = eventRemoteDataSource;
    }

    private void populateUserInfo(ArrayList<WaitlistEntry> waitlist) {
        for (WaitlistEntry entry : waitlist) {
            try {
                User user = userRemoteDataSource.getUserSync(entry.getUserId());
                if (user == null) {
                    String possibleEmail = decodeEmailKey(entry.getUserId());
                    if (possibleEmail != null) {
                        user = userRemoteDataSource.getUserByEmailSync(possibleEmail);
                        if (user == null) {
                            entry.setUserEmail(possibleEmail);
                        }
                    }
                }
                if (user != null) {
                    entry.setUserName(user.getName());
                    entry.setUserEmail(user.getEmail());
                }
            } catch (Exception e) {
                Log.e("RegistrationRepository", "Failed to fetch user info for " + entry.getUserId(), e);
            }
        }
    }

    private String decodeEmailKey(String key) {
        if (key == null || key.isEmpty()) return null;
        if (!key.contains("_at_")) return null;
        return key.replace("_at_", "@").replace("_", ".");
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
                Integer size = remoteDataSource.getWaitlistSizeSync(eventID);
                mainHandler.post(() -> callback.onResult(size));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Adds a user to the waitlist of the event atomically.
     *
     * <p>A lottery number is randomly generated and the timestamp is recorded for the entry.
     * Uses a Firestore transaction to check capacity and duplicate status, then writes the entry
     * and increments {@code Event.waitlistCount} in a single atomic operation — preventing
     * over-subscription under concurrent load.
     *
     * <p>Location (if provided) is attached to the entry <em>before</em> the transaction write
     * so it is persisted correctly.
     *
     * @param userID   the unique ID of the user in the database
     * @param eventID  the unique ID of the event in the database
     * @param location the user's current location, or {@code null} if not required
     * @param callback called when the operation completes; {@code error} is non-null on failure
     * @throws IllegalStateException    if the waitlist is full or closed
     * @throws IllegalArgumentException if the user is already on the waitlist
     */
    public void joinWaitlist(String userID, String eventID, Location location, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // Fetch user info to persist name/email for the map
                User user = userRemoteDataSource.getUserSync(userID);

                Random random = new Random();
                WaitlistEntry entry = new WaitlistEntry(
                        userID,
                        eventID,
                        WaitlistEntry.STATUS_WAITLISTED,
                        random.nextInt(100000),
                        System.currentTimeMillis()
                );

                // Set location BEFORE writing so it is included in the Firestore document.
                if (location != null) {
                    entry.setLatitude(location.getLatitude());
                    entry.setLongitude(location.getLongitude());
                }

                // Atomic join: checks duplicates + capacity, then writes entry in one transaction.
                remoteDataSource.joinWaitlistAtomicSync(eventID, entry);
                mainHandler.post(() -> callback.onComplete(null));

                if (user != null) {
                    entry.setUserName(user.getName());
                    entry.setUserEmail(user.getEmail());
                }

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * A separate join method specifically for guests. Uses a guest key for userId. Actual user
     * does not exist.
     *
     * @param guestKey   the unique ID of the user in the database
     * @param guestEmail the email entered by the guest
     * @param guestName the name entered by the guest
     * @param eventID  the unique ID of the event in the database
     * @param location the user's current location, or {@code null} if not required
     * @param callback called when the operation completes; {@code error} is non-null on failure
     * @throws IllegalStateException    if the waitlist is full or closed
     * @throws IllegalArgumentException if the user is already on the waitlist
     */
    public void joinWaitlistGuest(String guestKey, String guestEmail, String guestName, String eventID, Location location, VoidCallback callback) {
        executor.submit(() -> {
            try {
                Random random = new Random();
                WaitlistEntry entry = new WaitlistEntry(
                        guestKey,
                        eventID,
                        WaitlistEntry.STATUS_WAITLISTED,
                        random.nextInt(100000),
                        System.currentTimeMillis()
                );

                // Set location BEFORE writing so it is included in the Firestore document.
                if (location != null) {
                    entry.setLatitude(location.getLatitude());
                    entry.setLongitude(location.getLongitude());
                }

                // Atomic join: checks duplicates + capacity, then writes entry in one transaction.
                remoteDataSource.joinWaitlistAtomicSync(eventID, entry);
                remoteDataSource.addGuestFields(eventID, guestKey, guestEmail, guestName);
                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                Log.e("RegistrationRepository", "Error joining waitlist", e);
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    public void manuallyInviteEntrant(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                User user = userRemoteDataSource.getUserSync(userID);

                Random random = new Random();
                WaitlistEntry entry = new WaitlistEntry(
                        userID,
                        eventID,
                        WaitlistEntry.STATUS_INVITED,
                        random.nextInt(100000),
                        System.currentTimeMillis()
                );

                if (user != null) {
                    entry.setUserName(user.getName());
                    entry.setUserEmail(user.getEmail());
                }

                remoteDataSource.joinWaitlistSync(eventID, entry);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                Log.e("RegistrationRepository", "Error manually inviting entrant", e);
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
    public void inviteEntrant(String userID, String eventID, VoidCallback callback) {
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

                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_INVITED);
                if (callback != null) mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onComplete(e));
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

                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_CANCELLED);
                mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Runs the lottery that fairly selects which users to invite to an event. Each entrant is
     * given a lottery number upon joining the waitlist. The lottery loads in all waitlisted
     * entrants, sorts them in order of ascending lottery numbers, and invites entrants starting
     * from the front of the list until the event capacity is reached.
     *
     * @param eventID  the unique ID of the event in the database
     * @param callback called when the operation completes
     * @throws IllegalStateException the lottery has already been drawn; waitlist is empty
     */
    public void runLottery(String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                Event event = eventRemoteDataSource.getEventById(eventID);

                // logic checks
                if (event.isLotteryDrawn()) {
                    throw new IllegalStateException("The lottery has already been drawn.");
                }
                int size = remoteDataSource.getWaitlistSizeSync(eventID);
                if (size == 0) {
                    throw new IllegalStateException("Cannot draw lottery, waitlist is empty.")
;                }

                // execute operation
                int eventCapacity = event.getEventCapacity() != null ? event.getEventCapacity() : 0;

                ArrayList<WaitlistEntry> entries = remoteDataSource.getEntriesWithStatusSync(
                        eventID, WaitlistEntry.STATUS_WAITLISTED);

                Collections.sort(entries);

                int inviteCount = Math.min(eventCapacity, entries.size());
                for (int i = 0; i < inviteCount; i++) {
                    remoteDataSource.updateUserEntryStatusSync(
                            eventID, entries.get(i).getUserID(), WaitlistEntry.STATUS_INVITED);
                }

                // record lottery drawn
                event.setLotteryDrawn(true);
                eventRemoteDataSource.updateEvent(event);

                mainHandler.post(() -> callback.onComplete(null));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

    /**
     * Invites the next waitlisted user with the lowest lottery number.
     *
     * @param eventID  the unique ID of the event in the database
     * @param callback called when the operation completes
     */
    public void drawReplacement(String eventID, EntryCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> entries = remoteDataSource.getEntriesWithStatusSync(
                        eventID, WaitlistEntry.STATUS_WAITLISTED);
                Collections.sort(entries);

                WaitlistEntry replacement = entries.isEmpty() ? null : entries.get(0);
                if (replacement != null) {
                    remoteDataSource.updateUserEntryStatusSync(eventID, replacement.getUserID(), WaitlistEntry.STATUS_INVITED);
                }
                mainHandler.post(() -> callback.onResult(replacement));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
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
     * Gets all entries on the waitlist of a specific event, regardless of entry status.
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
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
     * Gets all entries on the waitlist of a specific event with status "waitlisted".
     *
     * @param eventID the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getWaitlisted(String eventID, WaitlistCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> waitlist = remoteDataSource.getEntriesWithStatusSync(
                        eventID, WaitlistEntry.STATUS_WAITLISTED);
                populateUserInfo(waitlist);
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
     * Gets all waitlist entries associated with a single user across all events.
     *
     * @param userID the unique ID of the user in the database
     * @param callback callback called when the operation completes
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

    /**
     * Callback interface for methods returning an ArrayList of WaitlistEntry.
     */
    public interface WaitlistCallback {
        void onResult(ArrayList<WaitlistEntry> waitlist);
    }

    /**
     * Callback interface for methods returning a WaitlistEntry.
     */
    public interface EntryCallback {
        void onResult(WaitlistEntry entry);
    }

    /**
     * Callback interface for methods returning a boolean.
     */
    public interface BooleanCallback {
        void onResult(Boolean value);
    }

    /**
     * Callback interface for methods returning an Integer.
     */
    public interface IntegerCallback {
        void onResult(Integer value);
    }

    /**
     * Attaches a real-time Firestore snapshot listener that delivers the current
     * waitlist count for an event whenever it changes.
     *
     * <p>The listener posts updates to the main thread via {@link #mainHandler} so
     * it is safe to update UI directly from {@code callback}.
     *
     * <p><b>Important:</b> The caller must call {@link ListenerRegistration#remove()}
     * on the returned registration in {@code onStop()} or {@code onDestroyView()} to
     * prevent memory leaks and unnecessary Firestore reads.
     *
     * @param eventID  the unique ID of the event to observe
     * @param callback receives the updated count (0 when the field is absent)
     * @return a {@link ListenerRegistration} that the caller must eventually remove
     */
    public ListenerRegistration observeWaitlistCount(String eventID, IntegerCallback callback) {
        return FirebaseFirestore.getInstance()
                .collection("events")
                .document(eventID)
                .addSnapshotListener((snap, err) -> {
                    if (snap == null || err != null) return;
                    Long count = snap.getLong("waitlistCount");
                    mainHandler.post(() -> callback.onResult(count != null ? count.intValue() : 0));
                });
    }

    /**
     * Loads entrant-specific stats: events joined and events won.
     * Events joined = count of registrations where status is not "cancelled" or "declined"
     * Events won = count of registrations where status is "invited" or "accepted"
     */
    public void getEntrantStats(String userId, EntrantStatsCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<WaitlistEntry> history = remoteDataSource.getHistoryForUserSync(userId);
                if (history == null) {
                    mainHandler.post(() -> callback.onResult(0, 0));
                    return;
                }

                final int[] counts = {0, 0}; // [eventsJoined, eventsWon]

                for (WaitlistEntry entry : history) {
                    String status = entry.getStatus();
                    if (status == null) continue;

                    // Count as joined if not cancelled or declined
                    if (!status.equals(WaitlistEntry.STATUS_CANCELLED) && !status.equals(WaitlistEntry.STATUS_DECLINED)) {
                        counts[0]++;
                    }

                    // Count as won if invited or accepted
                    if (status.equals(WaitlistEntry.STATUS_INVITED) || status.equals(WaitlistEntry.STATUS_ACCEPTED)) {
                        counts[1]++;
                    }
                }

                mainHandler.post(() -> callback.onResult(counts[0], counts[1]));
            } catch (Exception e) {
                Log.e("RegistrationRepository", "Error loading entrant stats", e);
                mainHandler.post(() -> callback.onResult(0, 0));
            }
        });
    }

    /**
     * Shuts down the background executor. Call from the owning lifecycle component's
     * onDestroy() / onTerminate() to prevent thread leaks.
     */
    public void shutdown() {
        executor.shutdown();
    }

    /**
     * Callback interface for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }

    /**
     * Callback interface for entrant stats (eventsJoined, eventsWon).
     */
    public interface EntrantStatsCallback {
        void onResult(int eventsJoined, int eventsWon);
    }
}