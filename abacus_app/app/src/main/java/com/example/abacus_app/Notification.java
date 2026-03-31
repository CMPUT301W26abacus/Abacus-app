package com.example.abacus_app;

public class Notification {

    public static final String TYPE_SELECTED = "SELECTED";
    public static final String TYPE_NOT_SELECTED = "NOT_SELECTED";

    private String userId;
    private String eventId;
    private String message;
    private String type;
    private long timestamp;

    public Notification() {}

    public Notification(String userId, String eventId, String message, String type) {
        this.userId = userId;
        this.eventId = eventId;
        this.message = message;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getUserId() { return userId; }
    public String getEventId() { return eventId; }
    public String getMessage() { return message; }
    public String getType() { return type; }
    public long getTimestamp() { return timestamp; }

    public void setUserId(String userId) { this.userId = userId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setMessage(String message) { this.message = message; }
    public void setType(String type) { this.type = type; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}