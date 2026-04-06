package com.example.abacus_app;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * RegistrationRepositoryTest.java
 *
 * Unit tests for waitlist join and leave business logic (US 01.01, US 01.02, US 01.05.04).
 * Pure Java — no Firebase, Android, or async dependencies required.
 *
 * Mirrors the approach used in EventBrowseUnitTest: the core business rules
 * are extracted into plain helper methods and tested directly, without
 * instantiating RegistrationRepository or any Firebase-dependent class.
 *
 * Business rules under test:
 * - A user can join a waitlist if they are not already on it and it is not full
 * - A user cannot join a waitlist they are already on
 * - A user cannot join a full waitlist
 * - A user can leave a waitlist they are on
 * - A user cannot leave a waitlist they are not on
 * - Joining/leaving one event does not affect other events
 * - The waitlist count reflects the number of active entries
 *
 * Covers:
 * - US 01.01 — Join the waiting list for a specific event
 * - US 01.02 — Leave the waiting list for a specific event
 * - US 01.05.04 — Total entrants on the waiting list
 */
public class RegistrationRepositoryTest {

    // ── In-memory waitlist store ──────────────────────────────────────────────

    /** Simulates the Firestore waitlist subcollection as a simple list. */
    private List<WaitlistEntry> store;

    @Before
    public void setUp() {
        store = new ArrayList<>();
    }

    // ── Helper methods mirroring RegistrationRepository business logic ────────

    /**
     * Attempts to add a user to the waitlist for an event.
     * Mirrors the duplicate and capacity checks in joinWaitlistAtomicSync.
     *
     * @param userId    the user joining
     * @param eventId   the event being joined
     * @param capacity  the max waitlist size, or null for unlimited
     * @return null on success, or an Exception describing the failure
     */
    private Exception join(String userId, String eventId, Integer capacity) {
        // Duplicate check
        for (WaitlistEntry e : store) {
            if (e.getUserId().equals(userId) && e.getEventId().equals(eventId)) {
                return new IllegalArgumentException("User is already on the waitlist.");
            }
        }
        // Capacity check
        if (capacity != null) {
            int count = countForEvent(eventId);
            if (count >= capacity) {
                return new IllegalStateException("Waitlist is full.");
            }
        }
        WaitlistEntry entry = new WaitlistEntry(
                userId, eventId, WaitlistEntry.STATUS_WAITLISTED, 0, System.currentTimeMillis());
        store.add(entry);
        return null;
    }

    /**
     * Attempts to remove a user from the waitlist for an event.
     * Mirrors the existence check in leaveWaitlist.
     *
     * @param userId  the user leaving
     * @param eventId the event being left
     * @return null on success, or an Exception describing the failure
     */
    private Exception leave(String userId, String eventId) {
        boolean found = store.removeIf(
                e -> e.getUserId().equals(userId) && e.getEventId().equals(eventId));
        if (!found) {
            return new IllegalArgumentException("This user was not on the waitlist.");
        }
        return null;
    }

    /**
     * Returns true if the user is currently on the waitlist for the event.
     */
    private boolean isOnWaitlist(String userId, String eventId) {
        for (WaitlistEntry e : store) {
            if (e.getUserId().equals(userId) && e.getEventId().equals(eventId)) return true;
        }
        return false;
    }

    /**
     * Returns the number of entries on the waitlist for the given event.
     */
    private int countForEvent(String eventId) {
        int count = 0;
        for (WaitlistEntry e : store) {
            if (e.getEventId().equals(eventId)) count++;
        }
        return count;
    }

    // ── US 01.01: Join waiting list ───────────────────────────────────────────

    /**
     * US 01.01 — A user can successfully join an event's waiting list.
     */
    @Test
    public void testJoin_success() {
        Exception error = join("user_1", "event_A", null);

        assertNull("Expected no error on join", error);
        assertTrue("User should be on waitlist after joining",
                isOnWaitlist("user_1", "event_A"));
    }

    /**
     * US 01.01 — Joining the same event twice returns an IllegalArgumentException.
     */
    @Test
    public void testJoin_duplicateFails() {
        join("user_1", "event_A", null);
        Exception error = join("user_1", "event_A", null);

        assertNotNull("Expected error when joining twice", error);
        assertTrue(error instanceof IllegalArgumentException);
    }

    /**
     * US 01.01 — Joining a full waitlist returns an IllegalStateException.
     */
    @Test
    public void testJoin_fullWaitlistFails() {
        join("user_1", "event_A", 1); // fills the waitlist
        Exception error = join("user_2", "event_A", 1);

        assertNotNull("Expected error when waitlist is full", error);
        assertTrue(error instanceof IllegalStateException);
    }

    /**
     * US 01.01 — Multiple different users can join the same event.
     */
    @Test
    public void testJoin_multipleUsersCanJoin() {
        join("user_A", "event_A", null);
        join("user_B", "event_A", null);
        join("user_C", "event_A", null);

        assertEquals(3, countForEvent("event_A"));
        assertTrue(isOnWaitlist("user_A", "event_A"));
        assertTrue(isOnWaitlist("user_B", "event_A"));
        assertTrue(isOnWaitlist("user_C", "event_A"));
    }

    /**
     * US 01.01 — Joining event A does not add the user to event B's waitlist.
     */
    @Test
    public void testJoin_doesNotAffectOtherEvents() {
        join("user_1", "event_A", null);

        assertFalse("User should not appear on a different event's waitlist",
                isOnWaitlist("user_1", "event_B"));
    }

    /**
     * US 01.01 — A user with unlimited capacity can always join.
     */
    @Test
    public void testJoin_unlimitedCapacityAlwaysAllows() {
        for (int i = 0; i < 100; i++) {
            Exception error = join("user_" + i, "event_A", null);
            assertNull("Join " + i + " should succeed with unlimited capacity", error);
        }
        assertEquals(100, countForEvent("event_A"));
    }

    // ── US 01.02: Leave waiting list ──────────────────────────────────────────

    /**
     * US 01.02 — A user can successfully leave a waiting list they are on.
     */
    @Test
    public void testLeave_success() {
        join("user_1", "event_A", null);
        Exception error = leave("user_1", "event_A");

        assertNull("Expected no error on leave", error);
        assertFalse("User should no longer be on waitlist after leaving",
                isOnWaitlist("user_1", "event_A"));
    }

    /**
     * US 01.02 — Attempting to leave a waitlist the user never joined returns an error.
     */
    @Test
    public void testLeave_notOnWaitlistFails() {
        Exception error = leave("user_1", "event_A");

        assertNotNull("Expected error when leaving a list the user never joined", error);
        assertTrue(error instanceof IllegalArgumentException);
    }

    /**
     * US 01.02 — Leaving only removes the specific user, not others on the same waitlist.
     */
    @Test
    public void testLeave_doesNotRemoveOtherUsers() {
        join("user_1", "event_A", null);
        join("user_2", "event_A", null);

        leave("user_1", "event_A");

        assertFalse("Leaving user should be off the waitlist",
                isOnWaitlist("user_1", "event_A"));
        assertTrue("Other user should still be on the waitlist",
                isOnWaitlist("user_2", "event_A"));
    }

    /**
     * US 01.02 — Leaving event A does not affect the user's status on event B.
     */
    @Test
    public void testLeave_doesNotAffectOtherEvents() {
        join("user_1", "event_A", null);
        join("user_1", "event_B", null);

        leave("user_1", "event_A");

        assertFalse("User should be off event_A waitlist",
                isOnWaitlist("user_1", "event_A"));
        assertTrue("User should still be on event_B waitlist",
                isOnWaitlist("user_1", "event_B"));
    }

    /**
     * US 01.02 — A user can rejoin a waitlist after leaving it.
     */
    @Test
    public void testLeaveAndRejoin() {
        join("user_1", "event_A", null);
        leave("user_1", "event_A");
        Exception error = join("user_1", "event_A", null);

        assertNull("Rejoining after leaving should succeed", error);
        assertTrue("User should be back on the waitlist",
                isOnWaitlist("user_1", "event_A"));
    }

    // ── US 01.05.04: Waitlist count ───────────────────────────────────────────

    /**
     * US 01.05.04 — Waitlist count is 0 when no users have joined.
     */
    @Test
    public void testCount_startsAtZero() {
        assertEquals(0, countForEvent("event_A"));
    }

    /**
     * US 01.05.04 — Count increments correctly as users join.
     */
    @Test
    public void testCount_incrementsOnJoin() {
        join("user_1", "event_A", null);
        assertEquals(1, countForEvent("event_A"));

        join("user_2", "event_A", null);
        assertEquals(2, countForEvent("event_A"));

        join("user_3", "event_A", null);
        assertEquals(3, countForEvent("event_A"));
    }

    /**
     * US 01.05.04 — Count decrements correctly when a user leaves.
     */
    @Test
    public void testCount_decrementsOnLeave() {
        join("user_1", "event_A", null);
        join("user_2", "event_A", null);
        assertEquals(2, countForEvent("event_A"));

        leave("user_1", "event_A");
        assertEquals(1, countForEvent("event_A"));
    }

    /**
     * US 01.05.04 — Count is per-event — joining event B does not affect event A's count.
     */
    @Test
    public void testCount_isPerEvent() {
        join("user_1", "event_A", null);
        join("user_2", "event_A", null);
        join("user_1", "event_B", null);

        assertEquals("event_A count should be 2", 2, countForEvent("event_A"));
        assertEquals("event_B count should be 1", 1, countForEvent("event_B"));
    }
}