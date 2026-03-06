package com.example.abacus_app;
import com.google.firebase.Timestamp;

/**
 *  Entity class that hold the data related to a single waitlist entry, including
 *  the user that joined the waitlist, their current status, their lotto number, and
 *  the timestamp of when they joined.
 *
 * @author Team Abacus
 * @version 1.0
 */
public class WaitlistEntry {

    public static final String STATUS_WAITLISTED = "waitlisted";
    public static final String STATUS_INVITED = "invited";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_CANCELLED = "cancelled";

    private final String userId;
    private String status; // "WAITLISTED", "INVITED", "ACCEPTED", "DECLINED", "CANCELLED"
    private final Integer lotteryNumber; // randomly assigned; used for lotto draw
    private final Timestamp joinTime;

    public WaitlistEntry(String userId, String status, Integer lotteryNumber, Timestamp joinTime) {
        this.userId = userId;
        this.status = status;
        this.lotteryNumber = lotteryNumber;
        this.joinTime = joinTime;
    }

    public String getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLotteryNumber() {
        return lotteryNumber;
    }

    public Timestamp getJoinTime() {
        return joinTime;
    }
}
