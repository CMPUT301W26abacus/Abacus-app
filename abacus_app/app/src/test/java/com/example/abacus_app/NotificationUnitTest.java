package com.example.abacus_app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the {@link Notification} model class.
 * This class performs basic validation of the model's logic, including constructor behavior,
 * getter accuracy, and setter functionality.
 *
 * Role: Ensures the integrity of the Notification POJO.
 * Design Pattern: Unit Testing (JUnit).
 *
 * Outstanding Issues:
 * - Does not test boundary conditions for timestamps (e.g., negative values).
 * - Only covers the data model; does not test integration with repositories or data sources.
 */
public class NotificationUnitTest {

    /**
     * Verifies that the parameterized constructor correctly initializes all fields
     * and that the timestamp is automatically generated and positive.
     */
    @Test
    public void testNotificationConstructorAndGetters() {
        String userId = "user123";
        String eventId = "event456";
        String message = "Congratulations!";
        String type = "SELECTED";

        Notification notification = new Notification(userId, eventId, message, type);

        assertEquals(userId, notification.getUserId());
        assertEquals(eventId, notification.getEventId());
        assertEquals(message, notification.getMessage());
        assertEquals(type, notification.getType());
        assertTrue("Timestamp should be positive", notification.getTimestamp() > 0);
    }

    /**
     * Verifies that the setter methods correctly update the internal state of a Notification object.
     * This is crucial for Firestore's deserialization process.
     */
    @Test
    public void testSetters() {
        Notification notification = new Notification();
        notification.setUserId("new_user");
        notification.setEventId("new_event");
        notification.setMessage("New Message");
        notification.setType("NOT_SELECTED");
        notification.setTimestamp(123456789L);

        assertEquals("new_user", notification.getUserId());
        assertEquals("new_event", notification.getEventId());
        assertEquals("New Message", notification.getMessage());
        assertEquals("NOT_SELECTED", notification.getType());
        assertEquals(123456789L, notification.getTimestamp());
    }
}
