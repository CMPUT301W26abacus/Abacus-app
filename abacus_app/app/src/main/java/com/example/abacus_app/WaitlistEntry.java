package com.example.abacus_app;
import com.google.firebase.Timestamp;

/**
 *  Entity class that hold the data related to a single waitlist entry, including the user that
 *  joined the waitlist, the event they joined, their current status, their lotto number, and the
 *  timestamp of when they joined.
 *
 * @author Team Abacus, Kaylee Crocker
 * @version 1.0
 */
public class WaitlistEntry {

    /**
     * Indicates that an entrant is on the waitlist but has not been invited.
     */
    /**
     * Indicates that an entrant is on the waitlist but has not been invited.
     */
    public static final String STATUS_WAITLISTED = "waitlisted";
    /**
     * Indicates that an entrant is invited to register for the event.
     */
    public static final String STATUS_INVITED = "invited";
    /**
     * Indicates that an entrant has accepted their invitation and registered for the event.
     */
    public static final String STATUS_ACCEPTED = "accepted";
    /**
     * Indicated that an entrant was invited but has chosen not to register for the event.
     */
    public static final String STATUS_DECLINED = "declined";
    /**
     * Indicates that an entrant has been removed from participating in the event by the organizer.
     */
    public static final String STATUS_CANCELLED = "cancelled";

    private String userID;
    private String eventID;
    private String status; // "WAITLISTED", "INVITED", "ACCEPTED", "DECLINED", "CANCELLED"
    private Integer lotteryNumber; // randomly assigned; used for lotto draw
    private Timestamp joinTime;

    /**
     * Default constructor required for Firebase. Do not use.
     */
    public WaitlistEntry() {}

    /**
     * Constructs an entry.
     *
     * @param userID the unique ID of the user in the database
     * @param status the current waitlist status of the entrant
     * @param lotteryNumber a random int used for fair lottery draw (hidden)
     * @param joinTime the timestamp at which the user joined the waitlist
     */
    public WaitlistEntry(String userID, String eventID, String status, Integer lotteryNumber, Timestamp joinTime) {
        this.userID = userID;
        this.eventID = eventID;
        this.status = status;
        this.lotteryNumber = lotteryNumber;
        this.joinTime = joinTime;
    }

    /**
     * Gets the user ID of the entrant.
     * @return the unique ID of the user in the database
     */
    public String getUserID() {
        return userID;
    }

    /**
     * Gets the event ID of the event.
     * @return the unique ID of the event in the database
     */
    public String getEventID() {
        return eventID;
    }

    /**
     * Gets the current status of the entrant.
     * @return the current waitlist status of the entrant
     */
    /**
     * Gets the current status of the entrant.
     * @return the current waitlist status of the entrant
     */
    public String getStatus() {
        return status;
    }

    /**
     * Gets the lottery number of the entrant. Should NOT be displayed to user.
     * @return a random int used for fair lottery draw (hidden)
     */
    public Integer getLotteryNumber() {
        return lotteryNumber;
    }

    /**
     * Gets the waitlist join time of the entrant.
     * @return the timestamp at which the user joined the waitlist
     */
    /**
     * Gets the waitlist join time of the entrant.
     * @return the timestamp at which the user joined the waitlist
     */
    public Timestamp getJoinTime() {
        return joinTime;
    }
}
