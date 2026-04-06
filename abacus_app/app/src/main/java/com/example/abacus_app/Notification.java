package com.example.abacus_app;

/**
 * Notification.java
 *
 * Role: Model/Entity in the Data Layer.
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

    private String userId;
    private String userEmail;
    private String organizerId;
    private String eventId;
    private String message;
    private String type;
    private String status = STATUS_PENDING;
    private long timestamp;
    
    /** 
     * Indicates if this notification should be visible in the user's personal inbox.
     * Set based on the user's 'notificationsEnabled' preference at the time of creation.
     */
    private boolean receivedInInbox = true; 

    public Notification() {}

    public Notification(String userId, String userEmail, String eventId, String message, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public Notification(String userId, String userEmail, String organizerId, String eventId, String message, String type) {
        this(userId, userEmail, eventId, message, type);
        this.organizerId = organizerId;
    }

    public String getUserId() { return userId; }
    public String getUserEmail() { return userEmail; }
    public String getOrganizerId() { return organizerId; }
    public String getEventId() { return eventId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public long getTimestamp() { return timestamp; }
    public boolean isReceivedInInbox() { return receivedInInbox; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setMessage(String message) { this.message = message; }
    public void setType(String type) { this.type = type; }
    public void setStatus(String status) { this.status = status; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public void setReceivedInInbox(boolean receivedInInbox) { this.receivedInInbox = receivedInInbox; }
}
