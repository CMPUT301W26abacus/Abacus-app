package com.example.abacus_app;

import com.google.firebase.Timestamp;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Date;

/**
 * Unit tests for the Event model class.
 * Tests basic getter/setter logic and object consistency for Himesh's user stories.
 * 
 * @author Himesh
 * @version 1.2
 */
public class EventUnitTest {

    /**
     * US 02.01.04 — Verifies that registration start and end times are correctly handled.
     * US 02.03.01 — Verifies that waitlist capacity can be set and retrieved.
     */
    @Test
    public void testEventConstructorAndGetters() {
        String eventId = "test_event_123";
        String title = "Winter Gala";
        String description = "Annual fundraising event.";
        String organizerId = "organizer_456";
        Date startDate = new Date();
        Date endDate = new Date(startDate.getTime() + 86400000); // +1 day
        Timestamp start = new Timestamp(startDate);
        Timestamp end = new Timestamp(endDate);
        Integer waitlistCap = 100;
        Integer eventCap = 50;
        boolean geo = true;

        Event event = new Event(eventId, title, description, organizerId, start, end, waitlistCap, eventCap, geo);

        assertEquals(eventId, event.getEventId());
        assertEquals(title, event.getTitle());
        assertEquals(description, event.getDescription());
        assertEquals(organizerId, event.getOrganizerId());
        assertEquals(start, event.getRegistrationStart());
        assertEquals(end, event.getRegistrationEnd());
        assertEquals(waitlistCap, event.getWaitlistCapacity());
        assertEquals(eventCap, event.getEventCapacity());
        assertTrue(event.isGeoRequired());
    }

    /**
     * US 02.04.01 / US 02.04.02 — Verifies that poster URLs can be updated.
     */
    @Test
    public void testSetters() {
        Event event = new Event();
        
        event.setEventId("new_id");
        assertEquals("new_id", event.getEventId());
        
        event.setTitle("Updated Title");
        assertEquals("Updated Title", event.getTitle());

        event.setPosterImageUrl("https://example.com/poster.png");
        assertEquals("https://example.com/poster.png", event.getPosterImageUrl());
        
        event.setQrCodeUrl("https://example.com/qr.png");
        assertEquals("https://example.com/qr.png", event.getQrCodeUrl());
        
        event.setGeoRequired(false);
        assertFalse(event.isGeoRequired());
    }

    /**
     * US 02.03.01 — Verifies that waitlist capacity is optional (can be null).
     */
    @Test
    public void testWaitlistCapacityOptional() {
        Event event = new Event();
        event.setWaitlistCapacity(null);
        assertNull(event.getWaitlistCapacity());
        
        event.setWaitlistCapacity(500);
        assertEquals(Integer.valueOf(500), event.getWaitlistCapacity());
    }
}
