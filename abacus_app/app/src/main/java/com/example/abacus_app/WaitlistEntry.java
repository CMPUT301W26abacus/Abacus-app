package com.example.abacus_app;

import com.google.firebase.firestore.Exclude;

/**
 * Entity class representing a single entry on an event's waitlist.
 * Maps directly to the 'registrations' collection in Firestore.
 * 
 * @author Himesh
 * @version 1.0
 */
public class WaitlistEntry {

    /** Status constants for the waitlist entry lifecycle */
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

    // Transient fields for UI display purposes, not stored in the registration document
    @Exclude
    private String userName;
    @Exclude
    private String userEmail;

    /**
     * Required no-arg constructor for Firebase Firestore deserialization.
     */
    public WaitlistEntry() {}

    /**
     * Constructs a new WaitlistEntry with specific details.
     *
     * @param userId        The unique ID of the entrant.
     * @param eventId       The unique ID of the event.
     * @param status        The initial status (e.g., "waitlisted").
     * @param lotteryNumber A random number used for selection.
     * @param timestamp     The time when the entrant joined the waitlist.
     */
    public WaitlistEntry(String userId, String eventId, String status, Integer lotteryNumber, Long timestamp) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = status;
        this.lotteryNumber = lotteryNumber;
        this.timestamp = timestamp;
    }

    /** @return The entrant's unique user ID. */
    public String getUserId() { return userId; }
    
    /** @param userId Sets the entrant's unique user ID. */
    public void setUserId(String userId) { this.userId = userId; }

    /** @return The unique ID of the event. */
    public String getEventId() { return eventId; }
    
    /** @param eventId Sets the unique ID of the event. */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /** @return The current status of the entrant (e.g., invited, accepted). */
    public String getStatus() { return status; }
    
    /** @param status Sets the current status of the entrant. */
    public void setStatus(String status) { this.status = status; }

    /** @return The Unix timestamp when the user registered. */
    public Long getTimestamp() { return timestamp; }
    
    /** @param timestamp Sets the Unix timestamp for the registration. */
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }

    /** @return The random lottery number assigned to this entry. */
    public Integer getLotteryNumber() { return lotteryNumber; }
    
    /** @param lotteryNumber Sets the random lottery number. */
    public void setLotteryNumber(Integer lotteryNumber) { this.lotteryNumber = lotteryNumber; }

    /** @return The user's name (fetched from User document). */
    @Exclude
    public String getUserName() { return userName; }

    /** @param userName Sets the user's name for UI display. */
    @Exclude
    public void setUserName(String userName) { this.userName = userName; }

    /** @return The user's email (fetched from User document). */
    @Exclude
    public String getUserEmail() { return userEmail; }

    /** @param userEmail Sets the user's email for UI display. */
    @Exclude
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    /** Compatibility getter for legacy code. @return User ID. */
    @Exclude
    public String getUserID() { return userId; }
    
    /** Compatibility getter for legacy code. @return Event ID. */
    @Exclude
    public String getEventID() { return eventId; }
}
