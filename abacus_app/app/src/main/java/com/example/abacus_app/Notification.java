package com.example.abacus_app;

/**
 * Entity class for a Notification.
 * Added 'userEmail' field to allow persistent identity across device re-installs.
 * Added 'organizerId' to track who sent the notification.
 * Added 'status' to track co-organizer invitation response.
 */
public class Notification {

    public static final String TYPE_SELECTED = "SELECTED";
    public static final String TYPE_NOT_SELECTED = "NOT_SELECTED";
    public static final String TYPE_CO_ORGANIZER_INVITE = "CO_ORGANIZER_INVITE";
    public static final String TYPE_CANCELED = "CANCELED";
    public static final String TYPE_MANUAL = "MANUAL";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_DECLINED = "DECLINED";

    private String userId;      // The entrant ID
    private String userEmail;   // Added for stable identity
    private String organizerId; // The ID of the organizer who sent the notification
    private String eventId;
    private String message;
    private String type;
    private String status = STATUS_PENDING; // Default status
    private long timestamp;

    // No-argument constructor required by Firestore deserialization
    public Notification() {}

    /**
     * Constructor for Notification without organizerId (for backward compatibility).
     */
    public Notification(String userId, String userEmail, String eventId, String message, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.status = STATUS_PENDING;
    }

    public Notification(String userId, String userEmail, String organizerId, String eventId, String message, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.organizerId = organizerId;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.status = STATUS_PENDING;
    }

    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public String getOrganizerId() { return organizerId; }
    public String getEventId() { return eventId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setMessage(String message) { this.message = message; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
