package com.example.abacus_app;

import com.google.firebase.firestore.Exclude;

/**
 * Entity class for a waitlist entry.
 * Fields match the Firestore 'registrations' collection.
 */
public class WaitlistEntry {

    /** Status constants */
    public static final String STATUS_WAITLISTED = "waitlisted";
    public static final String STATUS_INVITED    = "invited";
    public static final String STATUS_ACCEPTED   = "accepted";
    public static final String STATUS_DECLINED   = "declined";
    public static final String STATUS_CANCELLED  = "cancelled";

    private String userId;
    private String eventId;
    private String status;
    private Long timestamp;
    private Integer lotteryNumber;

    // Transient fields for UI
    @Exclude
    private String userName;
    @Exclude
    private String userEmail;

    /** Required no-arg constructor for Firestore */
    public WaitlistEntry() {}

    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Long timestamp) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = status;
        this.lotteryNumber = lotteryNumber;
        this.timestamp = timestamp;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getLotteryNumber() {
        return lotteryNumber;
    }

    public void setLotteryNumber(Integer lotteryNumber) {
        this.lotteryNumber = lotteryNumber;
    }

    @Exclude
    public String getUserName() {
        return userName;
    }

    @Exclude
    public void setUserName(String userName) {
        this.userName = userName;
    }

    @Exclude
    public String getUserEmail() {
        return userEmail;
    }

    @Exclude
    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    // Compatibility getters for existing code that might use uppercase ID
    @Exclude
    public String getUserID() { return userId; }
    @Exclude
    public String getEventID() { return eventId; }
}
