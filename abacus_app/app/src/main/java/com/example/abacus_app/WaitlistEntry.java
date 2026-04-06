package com.example.abacus_app;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

/**
 * Entity class representing a single entry in an event's waitlist.
 * Fields are mapped directly to Firestore 'waitlist' subcollection documents
 * under {@code events/{eventId}/waitlist/{userId}}.
 *
 * <p>Each entry tracks the user's current status in the lottery pipeline
 * (waitlisted → invited → accepted/declined), their lottery number used for
 * fair selection, their join timestamp, and optionally their geolocation
 * if the event requires it.
 *
 * @author Kaylee
 */
public class WaitlistEntry implements Comparable<WaitlistEntry> {

    /** Status value for users who have joined the waitlist and are awaiting the lottery draw. */
    public static final String STATUS_WAITLISTED = "waitlisted";

    /** Status value for users selected by the lottery and invited to the event. */
    public static final String STATUS_INVITED    = "invited";

    /** Status value for users who have accepted their lottery invitation. */
    public static final String STATUS_ACCEPTED   = "accepted";

    /** Status value for users who have declined their lottery invitation. */
    public static final String STATUS_DECLINED   = "declined";

    /** Status value for users whose accepted invitation was subsequently cancelled by the organizer. */
    public static final String STATUS_CANCELLED  = "cancelled";

    /** The user's unique identifier (UUID or guest email key). */
    private String userId;

    /** The Firestore document ID of the event this entry belongs to. */
    private String eventId;

    /** The current status of this entry — one of the STATUS_* constants. */
    private String status;

    /** Epoch-millisecond timestamp recording when the user joined the waitlist. */
    private Long timestamp;

    /**
     * Random integer assigned when the user joins, used for fair lottery selection.
     * The n entries with the lowest lottery numbers are invited when the lottery runs.
     * The value is secret from entrants.
     */
    private Integer lotteryNumber;

    /** The user's latitude at join time, or null if geolocation was not required. */
    private Double latitude;

    /** The user's longitude at join time, or null if geolocation was not required. */
    private Double longitude;

    /** The user's display name, persisted for UI display on the organizer map without extra lookups. */
    private String userName;

    /** The user's email, persisted for UI display on the organizer map without extra lookups. */
    private String userEmail;

    /**
     * Firebase Timestamp equivalent of {@link #timestamp}.
     * Stored for tests and code that uses getJoinTime(). Excluded from Firestore
     * serialisation to avoid duplicating the epoch-ms {@link #timestamp} field.
     */
    @Exclude private Timestamp joinTime;

    /** Required no-arg constructor for Firestore deserialisation. */
    public WaitlistEntry() {}

    /**
     * Primary constructor used by production code when adding a user to the waitlist.
     *
     * @param userId        the user's unique identifier
     * @param eventId       the event's Firestore document ID
     * @param status        the initial status (typically {@link #STATUS_WAITLISTED})
     * @param lotteryNumber a randomly assigned lottery number for fair draw ordering
     * @param timestamp     the join time as epoch milliseconds
     */
    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Long timestamp) {
        this.userId        = userId;
        this.eventId       = eventId;
        this.status        = status;
        this.lotteryNumber = lotteryNumber;
        this.timestamp     = timestamp;
    }

    /**
     * Overloaded constructor used by unit tests that supply a Firebase Timestamp.
     * Automatically derives the epoch-ms {@link #timestamp} from the given Timestamp.
     *
     * @param userId        the user's unique identifier
     * @param eventId       the event's Firestore document ID
     * @param status        the initial status (typically {@link #STATUS_WAITLISTED})
     * @param lotteryNumber a randomly assigned lottery number for fair draw ordering
     * @param joinTime      the join time as a Firebase Timestamp
     */
    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Timestamp joinTime) {
        this.userId        = userId;
        this.eventId       = eventId;
        this.status        = status;
        this.lotteryNumber = lotteryNumber;
        this.joinTime      = joinTime;
        this.timestamp     = joinTime != null ? joinTime.toDate().getTime() : null;
    }

    /** @return the user's unique identifier. */
    public String getUserId()              { return userId; }
    /** @param v the user's unique identifier. */
    public void   setUserId(String v)      { this.userId = v; }

    /** @return the Firestore document ID of the event this entry belongs to. */
    public String getEventId()             { return eventId; }
    /** @param v the event's Firestore document ID. */
    public void   setEventId(String v)     { this.eventId = v; }

    /** @return the current status of this entry — one of the STATUS_* constants. */
    public String getStatus()              { return status; }
    /** @param v the new status — one of the STATUS_* constants. */
    public void   setStatus(String v)      { this.status = v; }

    /** @return the join time as epoch milliseconds, or null if not set. */
    public Long   getTimestamp()           { return timestamp; }
    /** @param v the join time as epoch milliseconds. */
    public void   setTimestamp(Long v)     { this.timestamp = v; }

    /** @return the randomly assigned lottery number used for fair selection ordering. */
    public Integer getLotteryNumber()          { return lotteryNumber; }
    /** @param v the lottery number to assign. */
    public void    setLotteryNumber(Integer v) { this.lotteryNumber = v; }

    /** @return the user's latitude at join time, or null if geolocation was not required. */
    public Double getLatitude() { return latitude; }
    /** @param latitude the user's latitude at join time. */
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    /** @return the user's longitude at join time, or null if geolocation was not required. */
    public Double getLongitude() { return longitude; }
    /** @param longitude the user's longitude at join time. */
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    /** @return the user's display name, persisted for map display without extra Firestore reads. */
    public String getUserName()             { return userName; }
    /** @param v the user's display name. */
    public void   setUserName(String v)     { this.userName = v; }

    /** @return the user's email, persisted for map display without extra Firestore reads. */
    public String getUserEmail()            { return userEmail; }
    /** @param v the user's email address. */
    public void   setUserEmail(String v)    { this.userEmail = v; }

    /**
     * Returns the Firebase Timestamp set via the Timestamp constructor.
     * May be null for entries created via the epoch-ms constructor.
     * Excluded from Firestore serialisation.
     *
     * @return the join time as a Firebase Timestamp, or null
     */
    @Exclude public Timestamp getJoinTime()            { return joinTime; }

    /**
     * Sets the Firebase Timestamp for this entry.
     * Excluded from Firestore serialisation.
     *
     * @param v the join time as a Firebase Timestamp
     */
    @Exclude public void      setJoinTime(Timestamp v) { this.joinTime = v; }

    /**
     * Compatibility getter for code using the uppercase ID convention.
     * Excluded from Firestore serialisation to avoid duplicate fields.
     *
     * @return the user's unique identifier
     */
    @Exclude public String getUserID()  { return userId; }

    /**
     * Compatibility getter for code using the uppercase ID convention.
     * Excluded from Firestore serialisation to avoid duplicate fields.
     *
     * @return the event's Firestore document ID
     */
    @Exclude public String getEventID() { return eventId; }

    /**
     * Compares this entry to another by lottery number for use in the lottery draw.
     * Entries with lower lottery numbers are sorted first (ascending order).
     * Null lottery numbers sort last.
     *
     * @param other the other WaitlistEntry to compare against
     * @return negative if this entry's lottery number is lower, positive if higher, 0 if equal
     */
    @Override
    public int compareTo(WaitlistEntry other) {
        if (this.lotteryNumber == null && other.lotteryNumber == null) return 0;
        if (this.lotteryNumber == null) return 1;
        if (other.lotteryNumber == null) return -1;
        return Integer.compare(this.lotteryNumber, other.lotteryNumber);
    }
}