package com.example.abacus_app;

/**
 * Model class representing a notification sent to a user regarding an event.
 * This class follows the POJO (Plain Old Java Object) pattern for easy Firestore integration.
 * It stores details such as the target user, related event, message content, and timing.
 */
public class Notification {

    private String userId;
    private String eventId;
    private String message;
    private String type;
    private long timestamp;

    public Notification() {}

    /**
     * Constructs a new Notification with the specified details and sets the timestamp to the current time.
     *
     * @param userId  The unique identifier of the user who should receive this notification.
     * @param eventId The unique identifier of the event associated with this notification.
     * @param message The text content of the notification.
     * @param type    The category of notification (e.g., "win", "lose", "info").
     */
    public Notification(String userId, String eventId, String message, String type) {
        this.userId = userId;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * @return The unique identifier of the recipient user.
     */
    public String getUserId() { return userId; }

    /**
     * @return The unique identifier of the related event.
     */
    public String getEventId() { return eventId; }

    /**
     * @return The text content of the notification.
     */
    public String getMessage() { return message; }

    /**
     * @return The category or type of the notification.
     */
    public String getType() { return type; }

    /**
     * @return The time the notification was created, in milliseconds since epoch.
     */
    public long getTimestamp() { return timestamp; }

    /**
     * @param userId The unique identifier to set for the recipient user.
     */
    public void setUserId(String userId) { this.userId = userId; }

    /**
     * @param eventId The unique identifier to set for the related event.
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * @param message The text content to set for the notification.
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * @param type The category or type to set for the notification.
     */
    public void setType(String type) { this.type = type; }

    /**
     * @param timestamp The creation time to set, in milliseconds since epoch.
     */
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
