package com.example.abacus_app;

/**
 * Entity class for a comment.
 * Fields match the Firestore 'comments' documents.
 *
 * @author Kaylee
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

    /**
     * Constructs a comment with all data filled in.
     *
     * @param commentId the unique ID of the comment in the database
     * @param userId the unique ID of the user who posted the comment
     * @param username the display name of the user, stored for ease of access
     * @param eventId the unique ID of the event the comment is posted to
     * @param content the text the comment will display, written by user
     * @param timestamp the timestamp the comment was posted
     */
    public Comment(String commentId, String userId, String username, String eventId, String content, Long timestamp) {
        this.commentId = commentId;
        this.userId = userId;
        this.username = username;
        this.eventId = eventId;
        this.content = content;
        this.timestamp = timestamp;
    }

    /**
     * Gets the comment ID.
     * @return the unique ID of the comment in the database
     */
    public String getCommentId() {
        return commentId;
    }

    /**
     * Gets the ID of the user who left the comment.
     * @return the unique ID of user who left the comment
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Gets the name of the user who left the comment.
     * @return the name of user who left the comment
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the ID of the event the comment is about.
     * @return the unique ID of the event the comment is about
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Gets the text content of the comment written by the user who created it.
     * @return the text content of the comment
     */
    public String getContent() {
        return content;
    }

    /**
     * Gets the time the comment was posted.
     * @return the timestamp the comment was posted
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Only used for firebase serialization.
     * @param commentId the unique ID of the comment in the database
     */
    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    /**
     * Only used for firebase serialization.
     * @param userId the unique ID of user who left the comment
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Only used for firebase serialization.
     * @param username the name of user who left the comment
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Only used for firebase serialization.
     * @param eventId the unique ID of event the comment is about
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Only used for firebase serialization.
     * @param content the text content of the comment
     */
    public void setContent(String content) {
        this.content = content;
    }

    /**
     * Only used for firebase serialization.
     * @param timestamp the timestamp the comment was posted
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Compares Comment by timestamp to order from most recent to oldest.
     * @param other another comment to be compared to
     * @return a positive integer if sooner; zero if equal; negative if later
     */
    @Override
    public int compareTo(Comment other) {
        return Long.compare(this.getTimestamp(), other.getTimestamp());
    }
}
