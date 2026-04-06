package com.example.abacus_app;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Unit tests for the WaitlistEntry model class.
 * 
 * @author Himesh
 * @version 1.0
 */
public class WaitlistEntryUnitTest {

    /**
     * Tests the constructor and basic getters.
     */
    @Test
    public void testConstructorAndGetters() {
        String userId = "user_1";
        String eventId = "event_A";
        String status = WaitlistEntry.STATUS_WAITLISTED;
        Integer lotteryNum = 12345;
        Long timestamp = System.currentTimeMillis();

        WaitlistEntry entry = new WaitlistEntry(userId, eventId, status, lotteryNum, timestamp);

        assertEquals(userId, entry.getUserId());
        assertEquals(eventId, entry.getEventId());
        assertEquals(status, entry.getStatus());
        assertEquals(lotteryNum, entry.getLotteryNumber());
        assertEquals(timestamp, entry.getTimestamp());
    }

    /**
     * Tests the setters and transient UI fields.
     */
    @Test
    public void testSettersAndTransientFields() {
        WaitlistEntry entry = new WaitlistEntry();
        
        entry.setStatus(WaitlistEntry.STATUS_INVITED);
        assertEquals(WaitlistEntry.STATUS_INVITED, entry.getStatus());

        entry.setUserName("John Doe");
        assertEquals("John Doe", entry.getUserName());

        entry.setUserEmail("john@example.com");
        assertEquals("john@example.com", entry.getUserEmail());
    }

    /**
     * Tests compatibility getters.
     */
    @Test
    public void testCompatibilityGetters() {
        WaitlistEntry entry = new WaitlistEntry("U1", "E1", "waitlisted", 1, 100L);
        assertEquals("U1", entry.getUserID());
        assertEquals("E1", entry.getEventID());
    }
}
