package com.example.abacus_app;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Random;


/**
 *  Controller class that manages all actions related to the lottery and waitlist of a single event.
 *  <p>
 *  Responsibilities include updating the statuses of {@link WaitlistEntry}, running the random
 *  lottery selection algorithm, and handling business logic. The lottery works by assigning a
 *  random lottery number to each user as they join the waitlist (the lottery number is secret). At
 *  the time of the draw, the n lowest entries are the winners.
 *  </p>
 *
 * @author Team Abacus
 * @version 1.0
 */
public class RegistrationRepository {

    private final String eventID;
    private boolean lotteryDrawn;
    private boolean waitlistOpen;
    private Timestamp waitlistCloseTime;
    private int eventCapacity;
    private int waitlistCapacity;

    private RegistrationRemoteDataSource remote = new RegistrationRemoteDataSource();

    public RegistrationRepository(String eventID, Timestamp waitlistCloseTime, int eventCapacity, int waitlistCapacity) {
        this.eventID = eventID;
        this.waitlistCloseTime = waitlistCloseTime;
        this.eventCapacity = eventCapacity;
        this.waitlistCapacity = waitlistCapacity;
    }

    public String getEventID() {
        return eventID;
    }

    public boolean isLotteryDrawn() {
        return lotteryDrawn;
    }

    public void setLotteryDrawn(boolean lotteryDrawn) {
        this.lotteryDrawn = lotteryDrawn;
    }

    public boolean isWaitlistOpen() {
        return waitlistOpen;
    }

    public void setWaitlistOpen(boolean waitlistOpen) {
        this.waitlistOpen = waitlistOpen;
    }

    public Timestamp getWaitlistCloseTime() {
        return waitlistCloseTime;
    }

    public void setWaitlistCloseTime(Timestamp waitlistCloseTime) {
        this.waitlistCloseTime = waitlistCloseTime;
    }

    public int getEventCapacity() {
        return eventCapacity;
    }

    public void setEventCapacity(int eventCapacity) { // check with waitlist cap
        this.eventCapacity = eventCapacity;
    }

    public int getWaitlistCapacity() {
        return waitlistCapacity;
    }

    public void setWaitlistCapacity(int waitlistCapacity) { // check with current waitlist
        this.waitlistCapacity = waitlistCapacity;
    }

    /**
     * Gets the number of entrants currently on the waitlist, regardless of status.
     * @return the number of entrants on the waitlist
     */
    public int getWaitListSize() {
        return remote.getWaitlistSize(eventID);
    }

    /**
     * Adds a user to the waitlist of the event. Their lottery number is randomly generated and the
     * timestamp is recorded.
     *
     * @param userID the unique ID of the user in the database
     * @throws IllegalStateException the waitlist is full or closed
     * @throws IllegalArgumentException the user is already on the waitlist
     */
    public void joinWaitlist(String userID) {
        if (!waitlistOpen) {
            throw new IllegalStateException("The waitlist is closed.");
        } else if (getWaitListSize() >= waitlistCapacity) {
            throw new IllegalStateException("The waitlist is full.");
        } else if (isOnWaitlist(userID)) {
            throw new IllegalArgumentException("This user is already on the waitlist.");
        } else {
            int lotteryNumber = new Random().nextInt(10000);
            WaitlistEntry entry = new WaitlistEntry(userID, WaitlistEntry.STATUS_WAITLISTED, lotteryNumber, com.google.firebase.Timestamp.now());
            remote.joinWaitlist(eventID, entry);
        }
    }

    /**
     * Deletes the date related to the waitlist entry of the user.
     *
     * @param userID the unique ID of the user in the database
     * @throws IllegalArgumentException the given user is not on the waitlist
     */
    public void leaveWaitlist(String userID) {
        if (!isOnWaitlist(userID)) {
            throw new IllegalArgumentException("This user was not on the waitlist.");
        } else {
            remote.leaveWaitlist(eventID, userID);
        }
    }

    /**
     * Changes the status of an invited entrant from invited to accepted.
     *
     * @param userID the unique ID of the user in the database
     * @throws IllegalArgumentException the user is not on the waitlist
     * @throws IllegalStateException the user status is not invited
     */
    public void acceptInvitation(String userID) {
        if (!isOnWaitlist(userID)) {
            throw new IllegalArgumentException("This user is not on the waitlist.");
        } else if (!getUserEntry(userID).getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
            throw new IllegalStateException("User cannot accept invitation because they were not invited.");
        } else {
            remote.updateEntryStatus(eventID, userID, WaitlistEntry.STATUS_ACCEPTED);
        }
    }

    /**
     * Changes the status of an invited entrant from invited to declined.
     *
     * @param userID the unique ID of the user in the database
     * @throws IllegalArgumentException the user is not on the waitlist
     * @throws IllegalStateException the user status is not invited
     */
    public void declineInvitation(String userID) {
        if (!isOnWaitlist(userID)) {
            throw new IllegalArgumentException("This user is not on the waitlist.");
        } else if (!getUserEntry(userID).getStatus().equals(WaitlistEntry.STATUS_INVITED)) {
            throw new IllegalStateException("User cannot decline invitation because they were not invited.");
        } else {
            remote.updateEntryStatus(eventID, userID, WaitlistEntry.STATUS_DECLINED);
        }
    }

    public void cancelEntrant(String userID) {

    }

    public boolean isOnWaitlist(String userID) {
        throw new UnsupportedOperationException("TODO");
    }

    public WaitlistEntry getUserEntry(String userID) {
        if (!isOnWaitlist(userID)) {
            throw new IllegalArgumentException("This user is not on the waitlist.");
        } else {
            return remote.getWaitlistEntry(eventID, userID);
        }
    }

    public void runLottery() { // trigger notifications and send invited users

    }

    public void drawReplacement() { // also trigger notification

    }

    public ArrayList<WaitlistEntry> getAllEntries() {
        throw new UnsupportedOperationException("TODO");
    }

    public ArrayList<WaitlistEntry> getWaitlisted() {
        throw new UnsupportedOperationException("TODO");
    }

    public ArrayList<WaitlistEntry> getInvited() {
        throw new UnsupportedOperationException("TODO");
    }

    public ArrayList<WaitlistEntry> getAccepted() {
        throw new UnsupportedOperationException("TODO");
    }

    public ArrayList<WaitlistEntry> getDeclined() {
        throw new UnsupportedOperationException("TODO");
    }

    public ArrayList<WaitlistEntry> getCancelled() {
        throw new UnsupportedOperationException("TODO");
    }
}
