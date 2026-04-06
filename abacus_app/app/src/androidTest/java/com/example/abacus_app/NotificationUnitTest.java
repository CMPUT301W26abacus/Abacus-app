package com.example.abacus_app;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests for the Notification model.
 */
public class NotificationUnitTest {

    @Test
    public void constructor_withoutOrganizer_setsFieldsCorrectly() {
        Notification notification = new Notification(
                "user123",
                "user@example.com",
                "event456",
                "You were selected.",
                Notification.TYPE_SELECTED
        );

        assertEquals("user123", notification.getUserId());
        assertEquals("user@example.com", notification.getUserEmail());
        assertEquals("event456", notification.getEventId());
        assertEquals("You were selected.", notification.getMessage());
        assertEquals(Notification.TYPE_SELECTED, notification.getType());
        assertTrue(notification.getTimestamp() > 0);
        assertEquals(Notification.STATUS_PENDING, notification.getStatus());
        assertTrue(notification.isReceivedInInbox());
        assertNull(notification.getOrganizerId());
    }

    @Test
    public void constructor_withOrganizer_setsFieldsCorrectly() {
        Notification notification = new Notification(
                "user123",
                "user@example.com",
                "org789",
                "event456",
                "Manual organizer message",
                Notification.TYPE_MANUAL
        );

        assertEquals("user123", notification.getUserId());
        assertEquals("user@example.com", notification.getUserEmail());
        assertEquals("org789", notification.getOrganizerId());
        assertEquals("event456", notification.getEventId());
        assertEquals("Manual organizer message", notification.getMessage());
        assertEquals(Notification.TYPE_MANUAL, notification.getType());
        assertTrue(notification.getTimestamp() > 0);
        assertEquals(Notification.STATUS_PENDING, notification.getStatus());
        assertTrue(notification.isReceivedInInbox());
    }

    @Test
    public void setters_updateAllFieldsCorrectly() {
        Notification notification = new Notification();

        notification.setUserId("u1");
        notification.setUserEmail("u1@test.com");
        notification.setOrganizerId("org1");
        notification.setEventId("e1");
        notification.setMessage("Hello");
        notification.setType(Notification.TYPE_NOT_SELECTED);
        notification.setStatus(Notification.STATUS_ACCEPTED);
        notification.setTimestamp(123456L);
        notification.setReceivedInInbox(false);

        assertEquals("u1", notification.getUserId());
        assertEquals("u1@test.com", notification.getUserEmail());
        assertEquals("org1", notification.getOrganizerId());
        assertEquals("e1", notification.getEventId());
        assertEquals("Hello", notification.getMessage());
        assertEquals(Notification.TYPE_NOT_SELECTED, notification.getType());
        assertEquals(Notification.STATUS_ACCEPTED, notification.getStatus());
        assertEquals(123456L, notification.getTimestamp());
        assertFalse(notification.isReceivedInInbox());
    }

    @Test
    public void constants_haveExpectedValues() {
        assertEquals("SELECTED", Notification.TYPE_SELECTED);
        assertEquals("NOT_SELECTED", Notification.TYPE_NOT_SELECTED);
        assertEquals("CO_ORGANIZER_INVITE", Notification.TYPE_CO_ORGANIZER_INVITE);
        assertEquals("CANCELED", Notification.TYPE_CANCELED);
        assertEquals("MANUAL", Notification.TYPE_MANUAL);

        assertEquals("PENDING", Notification.STATUS_PENDING);
        assertEquals("ACCEPTED", Notification.STATUS_ACCEPTED);
        assertEquals("DECLINED", Notification.STATUS_DECLINED);
    }
}