package com.example.abacus_app;

import com.google.firebase.Timestamp;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for the WaitlistEntry model class.
 *
 * Covers:
 *   US 01.02.03 — View Registration History (status constants drive display labels)
 *
 * No Android APIs used — runs on JVM directly.
 * Note: Timestamp.now() requires Firebase — tests that need it use a fixed
 * Timestamp created via new Timestamp(seconds, nanos).
 */
@RunWith(JUnit4.class)
public class WaitlistEntryTest {

    // Fixed test timestamp: 2026-03-11 12:00:00 UTC
    private static final Timestamp TEST_TIMESTAMP = new Timestamp(1741694400L, 0);

    // ── Status constants ─────────────────────────────────────────────────────

    /** STATUS_WAITLISTED must equal "waitlisted" so Firestore string comparisons work. */
    @Test
    public void statusConstant_waitlisted_equalsExpectedString() {
        assertEquals("waitlisted", WaitlistEntry.STATUS_WAITLISTED);
    }

    /** STATUS_INVITED must equal "invited". */
    @Test
    public void statusConstant_invited_equalsExpectedString() {
        assertEquals("invited", WaitlistEntry.STATUS_INVITED);
    }

    /** STATUS_ACCEPTED must equal "accepted". */
    @Test
    public void statusConstant_accepted_equalsExpectedString() {
        assertEquals("accepted", WaitlistEntry.STATUS_ACCEPTED);
    }

    /** STATUS_DECLINED must equal "declined". */
    @Test
    public void statusConstant_declined_equalsExpectedString() {
        assertEquals("declined", WaitlistEntry.STATUS_DECLINED);
    }

    /** STATUS_CANCELLED must equal "cancelled". */
    @Test
    public void statusConstant_cancelled_equalsExpectedString() {
        assertEquals("cancelled", WaitlistEntry.STATUS_CANCELLED);
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    /** Constructor stores all fields and getters return correct values. */
    @Test
    public void constructor_storesAllFields() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-alice", "event-spring-gala",
                WaitlistEntry.STATUS_WAITLISTED, 423987, TEST_TIMESTAMP);

        assertEquals("user-alice",        entry.getUserID());
        assertEquals("event-spring-gala", entry.getEventID());
        assertEquals(WaitlistEntry.STATUS_WAITLISTED, entry.getStatus());
        assertEquals(Integer.valueOf(423987), entry.getLotteryNumber());
        assertEquals(TEST_TIMESTAMP,      entry.getJoinTime());
    }

    /** Default (no-arg) constructor required by Firestore does not throw. */
    @Test
    public void defaultConstructor_doesNotThrow() {
        WaitlistEntry entry = new WaitlistEntry();
        assertNotNull(entry);
    }

    /** getUserID returns the user ID set in the constructor. */
    @Test
    public void getUserID_returnsConstructorValue() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-bob", "event-xyz", WaitlistEntry.STATUS_INVITED, 100, TEST_TIMESTAMP);
        assertEquals("user-bob", entry.getUserID());
    }

    /** getEventID returns the event ID set in the constructor. */
    @Test
    public void getEventID_returnsConstructorValue() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-bob", "event-winter-concert", WaitlistEntry.STATUS_ACCEPTED, 50, TEST_TIMESTAMP);
        assertEquals("event-winter-concert", entry.getEventID());
    }

    /** getLotteryNumber returns the exact integer passed in. */
    @Test
    public void getLotteryNumber_returnsConstructorValue() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-carol", "event-abc", WaitlistEntry.STATUS_WAITLISTED, 999999, TEST_TIMESTAMP);
        assertEquals(Integer.valueOf(999999), entry.getLotteryNumber());
    }

    /** getJoinTime returns null when constructed with null timestamp (stale data guard). */
    @Test
    public void getJoinTime_nullTimestamp_returnsNull() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-dave", "event-abc", WaitlistEntry.STATUS_WAITLISTED, 1, null);
        assertNull(entry.getJoinTime());
    }

    /** getJoinTime returns the timestamp passed in. */
    @Test
    public void getJoinTime_withTimestamp_returnsTimestamp() {
        WaitlistEntry entry = new WaitlistEntry(
                "user-alice", "event-abc", WaitlistEntry.STATUS_WAITLISTED, 1, TEST_TIMESTAMP);
        assertEquals(TEST_TIMESTAMP, entry.getJoinTime());
    }

    // ── Status semantics ─────────────────────────────────────────────────────

    /** An entry constructed with STATUS_WAITLISTED reports that status. */
    @Test
    public void status_waitlisted_isStoredCorrectly() {
        WaitlistEntry entry = new WaitlistEntry(
                "u1", "e1", WaitlistEntry.STATUS_WAITLISTED, 1, TEST_TIMESTAMP);
        assertEquals(WaitlistEntry.STATUS_WAITLISTED, entry.getStatus());
    }

    /** An entry constructed with STATUS_INVITED reports that status. */
    @Test
    public void status_invited_isStoredCorrectly() {
        WaitlistEntry entry = new WaitlistEntry(
                "u1", "e1", WaitlistEntry.STATUS_INVITED, 1, TEST_TIMESTAMP);
        assertEquals(WaitlistEntry.STATUS_INVITED, entry.getStatus());
    }

    /** An entry constructed with STATUS_ACCEPTED reports that status. */
    @Test
    public void status_accepted_isStoredCorrectly() {
        WaitlistEntry entry = new WaitlistEntry(
                "u1", "e1", WaitlistEntry.STATUS_ACCEPTED, 1, TEST_TIMESTAMP);
        assertEquals(WaitlistEntry.STATUS_ACCEPTED, entry.getStatus());
    }

    /** An entry constructed with STATUS_DECLINED reports that status. */
    @Test
    public void status_declined_isStoredCorrectly() {
        WaitlistEntry entry = new WaitlistEntry(
                "u1", "e1", WaitlistEntry.STATUS_DECLINED, 1, TEST_TIMESTAMP);
        assertEquals(WaitlistEntry.STATUS_DECLINED, entry.getStatus());
    }

    /** An entry constructed with STATUS_CANCELLED reports that status. */
    @Test
    public void status_cancelled_isStoredCorrectly() {
        WaitlistEntry entry = new WaitlistEntry(
                "u1", "e1", WaitlistEntry.STATUS_CANCELLED, 1, TEST_TIMESTAMP);
        assertEquals(WaitlistEntry.STATUS_CANCELLED, entry.getStatus());
    }
}