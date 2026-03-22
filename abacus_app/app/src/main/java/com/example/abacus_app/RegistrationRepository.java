package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller class that manages all actions related to the lottery and waitlist.
 * Joins 'registrations' data with 'users' data for UI display.
 */
public class RegistrationRepository {

    private final RegistrationRemoteDataSource remoteDataSource;
    private final UserRemoteDataSource         userRemoteDataSource;
    private final ExecutorService              executor    = Executors.newSingleThreadExecutor();
    private final Handler                      mainHandler = new Handler(Looper.getMainLooper());

    public RegistrationRepository() {
        this.remoteDataSource     = new RegistrationRemoteDataSource();
        this.userRemoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
    }

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

    public void inviteEntrant(String userID, String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                remoteDataSource.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_INVITED);
                if (callback != null) mainHandler.post(() -> callback.onComplete(null));
            } catch (Exception e) {
                if (callback != null) mainHandler.post(() -> callback.onComplete(e));
            }
        });
    }

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
     * Runs the lottery that fairly selects which users to invite to an event. Each entrant is
     * given a lottery number upon joining the waitlist. The lottery loads in all waitlisted
     * entrants, sorts them in order of ascending lottery numbers, and invites entrants starting
     * from the front of the list until the event capacity is reached.
     *
     * @param eventID  the unique ID of the event in the database
     * @param callback called when the operation completes
     */
    public void runLottery(String eventID, VoidCallback callback) {
        executor.submit(() -> {
            try {
                // Fetch event capacity directly from Firestore
                DocumentSnapshot eventDoc = Tasks.await(
                        FirebaseFirestore.getInstance()
                                .collection("events")
                                .document(eventID)
                                .get());

                if (!eventDoc.exists()) {
                    mainHandler.post(() -> callback.onComplete(new Exception("Event not found")));
                    return;
                }

                Long capacityLong = eventDoc.getLong("eventCapacity");
                int eventCapacity = capacityLong != null ? capacityLong.intValue() : 0;

                ArrayList<WaitlistEntry> entries = remoteDataSource.getEntriesWithStatusSync(
                        eventID, WaitlistEntry.STATUS_WAITLISTED);

                Collections.sort(entries);

                int inviteCount = Math.min(eventCapacity, entries.size());
                for (int i = 0; i < inviteCount; i++) {
                    remoteDataSource.updateUserEntryStatusSync(
                            eventID, entries.get(i).getUserID(), WaitlistEntry.STATUS_INVITED);
                }
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
                mainHandler.post(() -> callback.onResult(replacement));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

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
     * Callback interface for methods returning ArrayList<WaitlistEntry>.
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
     * Callback interface for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }
}