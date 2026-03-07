package com.example.abacus_app;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.Random;

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
    private boolean lotteryDrawn;
    private boolean waitlistOpen;
    private Timestamp waitlistCloseTime;
    private int eventCapacity;
    private int waitlistCapacity;

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

    public void joinWaitlist(String userID) {
        int lotteryNumber = new Random().nextInt(1000000);
        WaitlistEntry entry = new WaitlistEntry(userID, WaitlistEntry.STATUS_WAITLISTED, lotteryNumber, Timestamp.now());
        RegistrationRemoteDataSource remote = new RegistrationRemoteDataSource();
        remote.joinWaitlist(eventID, entry);
    }

    public void leaveWaitlist(String userID) {
        // TODO: Implementation for removing user from waitlist
    }

    public void acceptInvitation(String userID) {
        // TODO: Update status to ACCEPTED
    }

    public void declineInvitation(String userID) {
        // TODO: Update status to DECLINED
    }

    public void cancelEntrant(String userId) {
        // TODO: Update status to CANCELLED
    }

    public void runLottery() {
        // TODO: Algorithm to select winners
    }

    public void drawReplacement() {
        // TODO: Draw one more winner from waitlist
    }

    public ArrayList<WaitlistEntry> getAll() {
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
