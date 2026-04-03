package com.example.abacus_app;

/**
 * Entity class for a Notification.
 * Added 'userEmail' field to allow persistent identity across device re-installs.
 */
public class Notification {

    public static final String TYPE_SELECTED = "SELECTED";
    public static final String TYPE_NOT_SELECTED = "NOT_SELECTED";
    public static final String TYPE_CANCELED = "CANCELED";

    private String userId;
    private String userEmail; // Added for stable identity
    private String eventId;
    private String message;
    private String type;
    private long timestamp;

    // No-argument constructor required by Firestore deserialization
    public Notification() {}

    public Notification(String userId, String eventId, String s, String typeSelected) {}

    public Notification(String userId, String userEmail, String eventId, String message, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public String getEventId() { return eventId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public long getTimestamp() { return timestamp; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setMessage(String message) { this.message = message; }
    public void setType(String type) { this.type = type; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
