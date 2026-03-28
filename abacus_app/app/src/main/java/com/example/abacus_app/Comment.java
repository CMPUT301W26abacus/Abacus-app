package com.example.abacus_app;

/**
 * Entity class for a comment.
 * Fields match the Firestore 'comments' documents.
 */
public class Comment implements Comparable<Comment>{

    private String commentId;
    private String userId;
    private String username;
    private String eventId;
    private String content;
    private Long timestamp;

    /**
     * Default constructor for FireBase.
     */
    public Comment() { }
    public Comment(String commentId, String userId, String username, String eventId, String content, Long timestamp) {
        this.commentId = commentId;
        this.userId = userId;
        this.username = username;
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

    public String getUsername() {
        return username;
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

    public void setUsername(String username) {
        this.username = username;
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

    /**
     * Compares Comment by timestamp to order from most recent to oldest.
     * @param other
     * @return
     */
    @Override
    public int compareTo(Comment other) {
        return Long.compare(this.getTimestamp(), other.getTimestamp());
    }
}
