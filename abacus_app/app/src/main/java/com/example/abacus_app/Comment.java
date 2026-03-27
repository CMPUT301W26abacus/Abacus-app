package com.example.abacus_app;

/**
 * Entity class for a comment.
 * Fields match the Firestore 'comments' documents.
 */
public class Comment { 

    private String commentId;
    private String userId;
    private String eventId;
    private String content;
    private Long timestamp;

    /**
     * Default constructor for FireBase.
     */
    Comment() { }
    public Comment(String commentId, String userId, String eventId, String content, Long timestamp) {
        this.commentId = commentId;
        this.userId = userId;
        this.eventId = eventId;
        this.content = content;
        this.timestamp = timestamp;
    }

    public String getCommentId() {
        return commentId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getContent() {
        return content;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
}
