package com.example.abacus_app;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller class that manages all actions related to the lottery and waitlist of
 * a single event.
 * <p>
 * Responsibilities include updating the statuses of {@link WaitlistEntry}, running
 * the random lottery selection algorithm, and handling business logic. The lottery
 * works by assigning a random lottery number to each user as they join the waitlist
 * (the lottery number is secret). At the time of the draw, the n lowest entries are
 * the winners.
 * </p>
 *
 * @author Team Abacus
 * @version 1.1
 */
public class RegistrationRepository {

    private final String eventID;
    private final RegistrationRemoteDataSource remote;
    private boolean lotteryDrawn;
    private boolean waitlistOpen;
    private Timestamp waitlistCloseTime;
    private int eventCapacity;
    private int waitlistCapacity;

    public RegistrationRepository(String eventID, Timestamp waitlistCloseTime,
                                  int eventCapacity, int waitlistCapacity) {
        this.eventID = eventID;
        this.waitlistCloseTime = waitlistCloseTime;
        this.eventCapacity = eventCapacity;
        this.waitlistCapacity = waitlistCapacity;
        this.remote = new RegistrationRemoteDataSource();
    }

    public String getEventID() { return eventID; }
    public boolean isLotteryDrawn() { return lotteryDrawn; }
    public void setLotteryDrawn(boolean lotteryDrawn) { this.lotteryDrawn = lotteryDrawn; }
    public boolean isWaitlistOpen() { return waitlistOpen; }
    public void setWaitlistOpen(boolean waitlistOpen) { this.waitlistOpen = waitlistOpen; }
    public Timestamp getWaitlistCloseTime() { return waitlistCloseTime; }
    public void setWaitlistCloseTime(Timestamp waitlistCloseTime) { this.waitlistCloseTime = waitlistCloseTime; }
    public int getEventCapacity() { return eventCapacity; }
    public void setEventCapacity(int eventCapacity) { this.eventCapacity = eventCapacity; }
    public int getWaitlistCapacity() { return waitlistCapacity; }
    public void setWaitlistCapacity(int waitlistCapacity) { this.waitlistCapacity = waitlistCapacity; }

    /**
     * Adds a user to the waitlist with a random lottery number.
     */
    public void joinWaitlist(String userID) {
        try {
            int lotteryNumber = new Random().nextInt(1000000);
            WaitlistEntry entry = new WaitlistEntry(userID, eventID, WaitlistEntry.STATUS_WAITLISTED,
                    lotteryNumber, Timestamp.now());
            remote.joinWaitlistSync(eventID, entry);
        } catch (Exception e) {
            throw new RuntimeException("Failed to join waitlist: " + e.getMessage(), e);
        }
    }

    /**
     * Removes a user from the waitlist entirely.
     */
    public void leaveWaitlist(String userID) {
        try {
            remote.removeWaitlistEntrySync(eventID, userID);
        } catch (Exception e) {
            throw new RuntimeException("Failed to leave waitlist: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the entrant's status to ACCEPTED after they confirm their invitation.
     */
    public void acceptInvitation(String userID) {
        try {
            remote.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_ACCEPTED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to accept invitation: " + e.getMessage(), e);
        }
    }

    /**
     * Updates the entrant's status to DECLINED if they choose not to register.
     */
    public void declineInvitation(String userID) {
        try {
            remote.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_DECLINED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decline invitation: " + e.getMessage(), e);
        }
    }

    /**
     * Cancels an entrant by updating their status to CANCELLED.
     */
    public void cancelEntrant(String userId) {
        try {
            remote.updateUserEntryStatusSync(eventID, userId, WaitlistEntry.STATUS_CANCELLED);
        } catch (Exception e) {
            throw new RuntimeException("Failed to cancel entrant: " + e.getMessage(), e);
        }
    }

    /**
     * Selects winners from the waitlist by picking the n lowest lottery numbers,
     * where n is the event capacity. Updates their status to INVITED.
     *
     * @param waitlist the current list of waitlisted entrants fetched from Firestore
     */
    public void runLottery(ArrayList<WaitlistEntry> waitlist) {
        try {
            waitlist.sort((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()));
            int inviteCount = Math.min(eventCapacity, waitlist.size());
            for (int i = 0; i < inviteCount; i++) {
                remote.updateUserEntryStatusSync(eventID, waitlist.get(i).getUserID(), WaitlistEntry.STATUS_INVITED);
            }
            lotteryDrawn = true;
        } catch (Exception e) {
            throw new RuntimeException("Failed to run lottery: " + e.getMessage(), e);
        }
    }

    /**
     * Draws one replacement winner from remaining WAITLISTED entrants,
     * picking the one with the lowest lottery number.
     *
     * @param waitlist the current list of all entrants fetched from Firestore
     */
    public void drawReplacement(ArrayList<WaitlistEntry> waitlist) {
        waitlist.stream()
                .filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus()))
                .min((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()))
                .ifPresent(e -> {
                    try {
                        remote.updateUserEntryStatusSync(eventID, e.getUserID(), WaitlistEntry.STATUS_INVITED);
                    } catch (Exception ex) {
                        throw new RuntimeException("Failed to update replacement status", ex);
                    }
                });
    }

    /**
     * These methods are not supported directly — Firestore queries are asynchronous.
     * Use remote.getWaitlist(eventID) and filter the results in your ViewModel or Fragment.
     */
    public ArrayList<WaitlistEntry> getAll() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() asynchronously.");
    }

    public ArrayList<WaitlistEntry> getWaitlisted() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() and filter by STATUS_WAITLISTED.");
    }

    public ArrayList<WaitlistEntry> getInvited() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() and filter by STATUS_INVITED.");
    }

    public ArrayList<WaitlistEntry> getAccepted() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() and filter by STATUS_ACCEPTED.");
    }

    public ArrayList<WaitlistEntry> getDeclined() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() and filter by STATUS_DECLINED.");
    }

    public ArrayList<WaitlistEntry> getCancelled() {
        throw new UnsupportedOperationException("Use remote.getWaitlist() and filter by STATUS_CANCELLED.");
    }
}