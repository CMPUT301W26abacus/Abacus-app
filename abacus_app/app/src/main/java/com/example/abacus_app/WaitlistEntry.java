package com.example.abacus_app;

import com.google.firebase.Timestamp;

/**
 * Entity class that holds the data related to a single waitlist entry.
 * Modified for Firestore compatibility.
 *
 * @author Team Abacus
 * @version 1.1
 */
public class WaitlistEntry {

    public static final String STATUS_WAITLISTED = "waitlisted";
    public static final String STATUS_INVITED = "invited";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_DECLINED = "declined";
    public static final String STATUS_CANCELLED = "cancelled";

    private String userId;
    private String status;
    private Integer lotteryNumber;
    private Timestamp joinTime;

    /**
     * No-argument constructor required for Firestore deserialization.
     */
    public WaitlistEntry() {}

    public WaitlistEntry(String userId, String status, Integer lotteryNumber, Timestamp joinTime) {
        this.userId = userId;
        this.status = status;
        this.lotteryNumber = lotteryNumber;
        this.joinTime = joinTime;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
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

    public void setLotteryNumber(Integer lotteryNumber) {
        this.lotteryNumber = lotteryNumber;
    }

    public Timestamp getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(Timestamp joinTime) {
        this.joinTime = joinTime;
    }
}
