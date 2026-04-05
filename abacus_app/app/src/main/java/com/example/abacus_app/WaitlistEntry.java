package com.example.abacus_app;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * Entity class for a waitlist entry.
 * Fields match the Firestore 'waitlist' documents.
 *
 * @author Kaylee
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

    // Persisted fields for UI display on map without extra lookups
    private String userName;
    private String userEmail;

    /** Firebase Timestamp — stored for tests and code that uses getJoinTime(). */
    @Exclude private Timestamp joinTime;

    /** Required no-arg constructor for Firestore */
    public WaitlistEntry() {}

    /** Primary constructor — accepts epoch-ms Long timestamp (used by production code). */
    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Long timestamp) {
        this.userId        = userId;
        this.eventId       = eventId;
        this.status        = status;
        this.lotteryNumber = lotteryNumber;
        this.timestamp     = timestamp;
    }

    /** Overloaded constructor — accepts a Firebase Timestamp (used by tests). */
    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Timestamp joinTime) {
        this.userId        = userId;
        this.eventId       = eventId;
        this.status        = status;
        this.lotteryNumber = lotteryNumber;
        this.joinTime      = joinTime;
        this.timestamp     = joinTime != null ? joinTime.toDate().getTime() : null;
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

    public String getUserName()             { return userName; }
    public void   setUserName(String v)     { this.userName = v; }

    public String getUserEmail()            { return userEmail; }
    public void   setUserEmail(String v)    { this.userEmail = v; }

    /** Returns the Firebase Timestamp set via the Timestamp constructor (may be null). */
    @Exclude public Timestamp getJoinTime()          { return joinTime; }
    @Exclude public void      setJoinTime(Timestamp v) { this.joinTime = v; }

    // Compatibility getters for code using uppercase ID
    @Exclude public String getUserID()  { return userId; }
    @Exclude public String getEventID() { return eventId; }

    /**
     * Compares WaitlistEntry by lottery numbers for lottery draw.
     * @param other
     * @return
     */
    @Override
    public int compareTo(WaitlistEntry other) {
        if (this.lotteryNumber == null && other.lotteryNumber == null) return 0;
        if (this.lotteryNumber == null) return 1;
        if (other.lotteryNumber == null) return -1;
        return Integer.compare(this.lotteryNumber, other.lotteryNumber);
    }
}