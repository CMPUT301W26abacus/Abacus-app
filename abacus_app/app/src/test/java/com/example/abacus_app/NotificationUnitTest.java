package com.example.abacus_app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link Notification} model class.
 */
public class NotificationUnitTest {

    @Test
    public void testNotificationConstructorAndGetters() {
        String userId = "user123";
        String userEmail = "test@example.com";
        String eventId = "event456";
        String message = "Congratulations!";
        String type = "SELECTED";

        Notification notification = new Notification(userId, userEmail, eventId, message, type);

        assertEquals(userId, notification.getUserId());
        assertEquals(userEmail, notification.getUserEmail());
        assertEquals(eventId, notification.getEventId());
        assertEquals(message, notification.getMessage());
        assertEquals(type, notification.getType());
        assertTrue("Timestamp should be positive", notification.getTimestamp() > 0);
    }

    @Test
    public void testSetters() {
        Notification notification = new Notification();
        notification.setUserId("new_user");
        notification.setUserEmail("new@example.com");
        notification.setEventId("new_event");
        notification.setMessage("New Message");
        notification.setType("NOT_SELECTED");
        notification.setTimestamp(123456789L);

        assertEquals("new_user", notification.getUserId());
        assertEquals("new@example.com", notification.getUserEmail());
        assertEquals("new_event", notification.getEventId());
        assertEquals("New Message", notification.getMessage());
        assertEquals("NOT_SELECTED", notification.getType());
        assertEquals(123456789L, notification.getTimestamp());
    }
}
