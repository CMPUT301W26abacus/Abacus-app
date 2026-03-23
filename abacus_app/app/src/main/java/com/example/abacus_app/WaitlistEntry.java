package com.example.abacus_app;

import com.google.firebase.firestore.Exclude;

/**
 * Entity class for a waitlist entry.
 * Fields match the Firestore 'registrations' collection.
 */
public class WaitlistEntry implements Comparable<WaitlistEntry> {

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
    private Double latitude;
    private Double longitude;

    // Transient fields for UI display
    @Exclude private String userName;
    @Exclude private String userEmail;

    /** Required no-arg constructor for Firestore */
    public WaitlistEntry() {}

    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Long timestamp) {
        this.userId        = userId;
        this.eventId       = eventId;
        this.status        = status;
        this.lotteryNumber = lotteryNumber;
        this.timestamp     = timestamp;
    }

    public String getUserId()              { return userId; }
    public void   setUserId(String v)      { this.userId = v; }

    public String getEventId()             { return eventId; }
    public void   setEventId(String v)     { this.eventId = v; }

    public String getStatus()              { return status; }
    public void   setStatus(String v)      { this.status = v; }

    public Long   getTimestamp()           { return timestamp; }
    public void   setTimestamp(Long v)     { this.timestamp = v; }

    public Integer getLotteryNumber()          { return lotteryNumber; }
    public void    setLotteryNumber(Integer v) { this.lotteryNumber = v; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    @Exclude public String getUserName()             { return userName; }
    @Exclude public void   setUserName(String v)     { this.userName = v; }

    @Exclude public String getUserEmail()            { return userEmail; }
    @Exclude public void   setUserEmail(String v)    { this.userEmail = v; }

    // Compatibility getters for code using uppercase ID
    @Exclude public String getUserID()  { return userId; }
    @Exclude public String getEventID() { return eventId; }

    @Override
    public int compareTo(WaitlistEntry other) {
        return Integer.compare(this.getLotteryNumber(), other.getLotteryNumber());
    }
}