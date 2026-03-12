package com.example.abacus_app;

import com.google.firebase.Timestamp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Date;

/**
 * Unit tests for the Event model class.
 * Tests basic getter/setter logic and object consistency.
 * 
 * @author Himesh
 */
public class EventUnitTest {

    @Test
    public void testEventConstructorAndGetters() {
        String eventId = "test_event_id";
        String title = "Charity Gala";
        String description = "A night of giving.";
        String organizerId = "org_123";
        Timestamp start = new Timestamp(new Date());
        Timestamp end = new Timestamp(new Date());
        Integer capacity = 50;
        boolean geo = true;

        Event event = new Event(eventId, title, description, organizerId, start, end, capacity, geo);

        assertEquals(eventId, event.getEventId());
        assertEquals(title, event.getTitle());
        assertEquals(description, event.getDescription());
        assertEquals(organizerId, event.getOrganizerId());
        assertEquals(start, event.getRegistrationStart());
        assertEquals(end, event.getRegistrationEnd());
        assertEquals(capacity, event.getWaitlistCapacity());
        assertTrue(event.isGeoRequired());
    }

    @Test
    public void testSetters() {
        Event event = new Event();
        event.setTitle("Old Title");
        event.setTitle("New Title");
        assertEquals("New Title", event.getTitle());

        event.setPosterImageUrl("https://example.com/poster.jpg");
        assertEquals("https://example.com/poster.jpg", event.getPosterImageUrl());
    }

    @Test
    public void testWaitlistCapacityNullable() {
        Event event = new Event();
        event.setWaitlistCapacity(null);
        assertNull(event.getWaitlistCapacity());
    }
}
