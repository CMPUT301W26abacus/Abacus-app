package com.example.abacus_app;

/**
 * Notification.java
 *
 * This class represents a notification entity within the Abacus application.
 * It follows the Plain Old Java Object (POJO) pattern for easy serialization/deserialization with Firestore.
 * It stores details about messages sent to users, including recipient info, sender info, event context,
 * and the nature of the notification (e.g., selection results, invitations, or manual messages).
 *
 * Role: Model/Entity in the Data Layer.
 *
 * Outstanding Issues:
 * - Consider adding a 'read' status to track if the user has seen the notification.
 */
public class Notification {

    /** Type for users selected by the lottery. */
    public static final String TYPE_SELECTED = "SELECTED";
    /** Type for users not selected by the lottery. */
    public static final String TYPE_NOT_SELECTED = "NOT_SELECTED";
    /** Type for co-organizer invitations. */
    public static final String TYPE_CO_ORGANIZER_INVITE = "CO_ORGANIZER_INVITE";
    /** Type for cancelled event invitations. */
    public static final String TYPE_CANCELED = "CANCELED";
    /** Type for custom manual messages sent by organizers. */
    public static final String TYPE_MANUAL = "MANUAL";

    public static final String TYPE_COMMENT_DELETED = "COMMENT_DELETED";

    /** Default pending status for actionable notifications (e.g., invites). */
    public static final String STATUS_PENDING = "PENDING";
    /** Status when an invite has been accepted. */
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    /** Status when an invite has been declined. */
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

    /**
     * Default no-argument constructor required for Firebase Firestore deserialization.
     */
    public Notification() {}

    /**
     * Constructs a new Notification without an organizer ID.
     *
     * @param userId    Unique ID of the recipient user.
     * @param userEmail Email of the recipient user.
     * @param eventId   ID of the associated event.
     * @param message   The text content of the notification.
     * @param type      The classification type of the notification.
     */
    public Notification(String userId, String userEmail, String eventId, String message, String type) {
        this.userId = userId;
        this.userEmail = userEmail;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructs a new Notification including the sender's organizer ID.
     *
     * @param userId      Unique ID of the recipient user.
     * @param userEmail   Email of the recipient user.
     * @param organizerId Unique ID of the organizer sending the notification.
     * @param eventId     ID of the associated event.
     * @param message     The text content of the notification.
     * @param type        The classification type of the notification.
     */
    public Notification(String userId, String userEmail, String organizerId, String eventId, String message, String type) {
        this(userId, userEmail, eventId, message, type);
        this.organizerId = organizerId;
    }

    /** @return The recipient's unique user ID. */
    public String getUserId() { return userId; }
    /** @return The recipient's email address. */
    public String getUserEmail() { return userEmail; }
    /** @return The ID of the organizer who sent the notification. */
    public String getOrganizerId() { return organizerId; }
    /** @return The ID of the event associated with this notification. */
    public String getEventId() { return eventId; }
    /** @return The notification message text. */
    public String getMessage() { return message; }
    /** @return The type of notification (e.g., SELECTED, MANUAL). */
    public String getType() { return type; }
    /** @return The current status of the notification (e.g., PENDING, ACCEPTED). */
    public String getStatus() { return status; }
    /** @return The unix timestamp when the notification was created. */
    public long getTimestamp() { return timestamp; }
    /** @return True if this notification should be visible in the user's inbox. */
    public boolean isReceivedInInbox() { return receivedInInbox; }

    /** @param userId The recipient's unique user ID. */
    public void setUserId(String userId) { this.userId = userId; }
    /** @param userEmail The recipient's email address. */
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    /** @param organizerId The ID of the organizer who sent the notification. */
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    /** @param eventId The ID of the event associated with this notification. */
    public void setEventId(String eventId) { this.eventId = eventId; }
    /** @param message The notification message text. */
    public void setMessage(String message) { this.message = message; }
    /** @param type The type of notification. */
    public void setType(String type) { this.type = type; }
    /** @param status The current status of the notification. */
    public void setStatus(String status) { this.status = status; }
    /** @param timestamp The unix timestamp when the notification was created. */
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    /** @param receivedInInbox True if this notification should be visible in the user's inbox. */
    public void setReceivedInInbox(boolean receivedInInbox) { this.receivedInInbox = receivedInInbox; }
}
