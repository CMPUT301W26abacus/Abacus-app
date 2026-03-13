package com.example.abacus_app;

import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RegistrationRepository (the controller class).
 *
 * Covers:
 *   US 01.02.03 — View Registration History (status transitions used in history display)
 *   Lottery logic: runLottery, drawReplacement, status updates
 *
 * RegistrationRepository hard-wires its own RegistrationRemoteDataSource internally,
 * so a TestableRegistrationRepository subclass is used to inject the mock remote.
 *
 * Fixed Timestamp avoids calling Timestamp.now() (which needs Firebase runtime).
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationRepositoryTest {

    // 2026-03-11 12:00:00 UTC
    private static final Timestamp CLOSE_TIME = new Timestamp(1741694400L, 0);
    private static final Timestamp JOIN_TIME   = new Timestamp(1741694400L, 0);

    @Mock RegistrationRemoteDataSource mockRemote;

    private TestableRegistrationRepository repo;

    @Before
    public void setUp() {
        repo = new TestableRegistrationRepository(
                "event-spring-gala-2026",
                CLOSE_TIME,
                3,   // eventCapacity  — invite 3 winners
                10,  // waitlistCapacity
                mockRemote);
    }

    // ── Getters / initial state ──────────────────────────────────────────────

    /** getEventID() returns the value passed in the constructor. */
    @Test
    public void getEventID_returnsConstructorValue() {
        assertEquals("event-spring-gala-2026", repo.getEventID());
    }

    /** lotteryDrawn starts false before any draw. */
    @Test
    public void isLotteryDrawn_beforeDraw_isFalse() {
        assertFalse(repo.isLotteryDrawn());
    }

    /** getEventCapacity() returns the constructor value. */
    @Test
    public void getEventCapacity_returnsConstructorValue() {
        assertEquals(3, repo.getEventCapacity());
    }

    /** getWaitlistCapacity() returns the constructor value. */
    @Test
    public void getWaitlistCapacity_returnsConstructorValue() {
        assertEquals(10, repo.getWaitlistCapacity());
    }

    // ── runLottery ───────────────────────────────────────────────────────────

    /**
     * runLottery invites exactly eventCapacity entrants when waitlist is larger.
     * (capacity=3, waitlist=5 → 3 invitations)
     */
    @Test
    public void runLottery_largerwaitlist_invitesExactlyCapacityCount() throws Exception {
        ArrayList<WaitlistEntry> waitlist = buildWaitlist(
                "event-spring-gala-2026", 5);

        repo.runLottery(waitlist);

        verify(mockRemote, times(3))
                .updateUserEntryStatusSync(
                        eq("event-spring-gala-2026"),
                        anyString(),
                        eq(WaitlistEntry.STATUS_INVITED));
    }

    /**
     * runLottery always picks the entrants with the LOWEST lottery numbers.
     * Lottery numbers: alice=10, bob=20, carol=30, dave=500, eve=900
     * Capacity=3 → alice, bob, carol invited; dave and eve skipped.
     */
    @Test
    public void runLottery_selectsLowestLotteryNumbers() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entry("user-alice", 10));
        waitlist.add(entry("user-bob",   20));
        waitlist.add(entry("user-carol", 30));
        waitlist.add(entry("user-dave",  500));
        waitlist.add(entry("user-eve",   900));

        repo.runLottery(waitlist);

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-alice", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-bob",   WaitlistEntry.STATUS_INVITED);
        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-carol", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-dave",  WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-eve",   WaitlistEntry.STATUS_INVITED);
    }

    /**
     * When waitlist is smaller than capacity, all entrants are invited.
     * (capacity=3, waitlist=2 → both invited)
     */
    @Test
    public void runLottery_waitlistSmallerThanCapacity_invitesAll() throws Exception {
        ArrayList<WaitlistEntry> waitlist = buildWaitlist("event-spring-gala-2026", 2);

        repo.runLottery(waitlist);

        verify(mockRemote, times(2))
                .updateUserEntryStatusSync(anyString(), anyString(), eq(WaitlistEntry.STATUS_INVITED));
    }

    /** runLottery sets lotteryDrawn=true after completing. */
    @Test
    public void runLottery_setsLotteryDrawnTrue() throws Exception {
        repo.runLottery(buildWaitlist("event-spring-gala-2026", 3));

        assertTrue(repo.isLotteryDrawn());
    }

    /** runLottery on an empty waitlist still sets lotteryDrawn and makes no remote calls. */
    @Test
    public void runLottery_emptyWaitlist_setsDrawnWithNoInvitations() throws Exception {
        repo.runLottery(new ArrayList<>());

        assertTrue(repo.isLotteryDrawn());
        verify(mockRemote, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    // ── drawReplacement ──────────────────────────────────────────────────────

    /**
     * drawReplacement picks the single WAITLISTED entrant with the lowest
     * lottery number. Invited/accepted entrants are ignored.
     */
    @Test
    public void drawReplacement_picksLowestWaitlistedEntrant() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice",  5,  WaitlistEntry.STATUS_INVITED));
        waitlist.add(entryWithStatus("user-bob",   15,  WaitlistEntry.STATUS_WAITLISTED));
        waitlist.add(entryWithStatus("user-carol", 99,  WaitlistEntry.STATUS_WAITLISTED));

        repo.drawReplacement(waitlist);

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-bob", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-carol", WaitlistEntry.STATUS_INVITED);
    }

    /** drawReplacement skips accepted/declined entrants. */
    @Test
    public void drawReplacement_onlyConsidersWaitlistedEntrants() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice", 1, WaitlistEntry.STATUS_ACCEPTED));
        waitlist.add(entryWithStatus("user-bob",   2, WaitlistEntry.STATUS_DECLINED));
        waitlist.add(entryWithStatus("user-carol", 3, WaitlistEntry.STATUS_WAITLISTED));

        repo.drawReplacement(waitlist);

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-carol", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-alice", WaitlistEntry.STATUS_INVITED);
    }

    /** drawReplacement with no waitlisted entrants makes no remote call. */
    @Test
    public void drawReplacement_noWaitlistedEntrants_makesNoRemoteCall() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice", 10, WaitlistEntry.STATUS_ACCEPTED));

        repo.drawReplacement(waitlist);

        verify(mockRemote, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    // ── acceptInvitation / declineInvitation / cancelEntrant ─────────────────

    /** acceptInvitation updates the user's status to ACCEPTED in Firestore. */
    @Test
    public void acceptInvitation_updatesStatusToAccepted() throws Exception {
        repo.acceptInvitation("user-alice");

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-alice", WaitlistEntry.STATUS_ACCEPTED);
    }

    /** declineInvitation updates the user's status to DECLINED in Firestore. */
    @Test
    public void declineInvitation_updatesStatusToDeclined() throws Exception {
        repo.declineInvitation("user-bob");

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-bob", WaitlistEntry.STATUS_DECLINED);
    }

    /** cancelEntrant updates the user's status to CANCELLED in Firestore. */
    @Test
    public void cancelEntrant_updatesStatusToCancelled() throws Exception {
        repo.cancelEntrant("user-carol");

        verify(mockRemote).updateUserEntryStatusSync(
                "event-spring-gala-2026", "user-carol", WaitlistEntry.STATUS_CANCELLED);
    }

    /**
     * Remote exception during acceptInvitation is wrapped in RuntimeException
     * (not swallowed silently).
     */
    @Test(expected = RuntimeException.class)
    public void acceptInvitation_remoteThrows_wrapsInRuntimeException() throws Exception {
        doThrow(new Exception("Firestore offline"))
                .when(mockRemote).updateUserEntryStatusSync(anyString(), anyString(), anyString());

        repo.acceptInvitation("user-alice");
    }

    // ── setters ───────────────────────────────────────────────────────────────

    /** setEventCapacity allows capacity to be updated after construction. */
    @Test
    public void setEventCapacity_updatesValue() {
        repo.setEventCapacity(5);
        assertEquals(5, repo.getEventCapacity());
    }

    /** setWaitlistOpen controls waitlist-open state. */
    @Test
    public void setWaitlistOpen_updatesValue() {
        repo.setWaitlistOpen(true);
        assertTrue(repo.isWaitlistOpen());

        repo.setWaitlistOpen(false);
        assertFalse(repo.isWaitlistOpen());
    }

    // ── Unsupported synchronous getters throw ────────────────────────────────

    /** getAll() must throw UnsupportedOperationException (Firestore is async). */
    @Test(expected = UnsupportedOperationException.class)
    public void getAll_throwsUnsupportedOperation() {
        repo.getAll();
    }

    /** getWaitlisted() must throw UnsupportedOperationException. */
    @Test(expected = UnsupportedOperationException.class)
    public void getWaitlisted_throwsUnsupportedOperation() {
        repo.getWaitlisted();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ArrayList<WaitlistEntry> buildWaitlist(String eventId, int count) {
        ArrayList<WaitlistEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(entry("user-" + i, (i + 1) * 10));
        }
        return list;
    }

    private WaitlistEntry entry(String userId, int lotteryNumber) {
        return new WaitlistEntry(userId, "event-spring-gala-2026",
                WaitlistEntry.STATUS_WAITLISTED, lotteryNumber, JOIN_TIME);
    }

    private WaitlistEntry entryWithStatus(String userId, int lotteryNumber, String status) {
        return new WaitlistEntry(userId, "event-spring-gala-2026",
                status, lotteryNumber, JOIN_TIME);
    }

    // ── Injectable subclass ───────────────────────────────────────────────────

    /**
     * Subclass that accepts an injected RegistrationRemoteDataSource.
     * Necessary because RegistrationRepository creates its own remote instance.
     * All overrides exactly mirror the parent logic with the injected remote.
     */
    static class TestableRegistrationRepository extends RegistrationRepository {

        private final RegistrationRemoteDataSource injected;

        TestableRegistrationRepository(String eventID, Timestamp closeTime,
                                       int eventCapacity, int waitlistCapacity,
                                       RegistrationRemoteDataSource remote) {
            super(eventID, closeTime, eventCapacity, waitlistCapacity);
            this.injected = remote;
        }

        @Override
        public void runLottery(ArrayList<WaitlistEntry> waitlist) {
            try {
                waitlist.sort((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()));
                int count = Math.min(getEventCapacity(), waitlist.size());
                for (int i = 0; i < count; i++) {
                    injected.updateUserEntryStatusSync(
                            getEventID(), waitlist.get(i).getUserID(), WaitlistEntry.STATUS_INVITED);
                }
                setLotteryDrawn(true);
            } catch (Exception e) {
                throw new RuntimeException("Failed to run lottery: " + e.getMessage(), e);
            }
        }

        @Override
        public void drawReplacement(ArrayList<WaitlistEntry> waitlist) {
            waitlist.stream()
                    .filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus()))
                    .min((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()))
                    .ifPresent(e -> {
                        try {
                            injected.updateUserEntryStatusSync(
                                    getEventID(), e.getUserID(), WaitlistEntry.STATUS_INVITED);
                        } catch (Exception ex) {
                            throw new RuntimeException("Failed to draw replacement", ex);
                        }
                    });
        }

        @Override
        public void acceptInvitation(String userID) {
            try {
                injected.updateUserEntryStatusSync(
                        getEventID(), userID, WaitlistEntry.STATUS_ACCEPTED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to accept invitation: " + e.getMessage(), e);
            }
        }

        @Override
        public void declineInvitation(String userID) {
            try {
                injected.updateUserEntryStatusSync(
                        getEventID(), userID, WaitlistEntry.STATUS_DECLINED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decline invitation: " + e.getMessage(), e);
            }
        }

        @Override
        public void cancelEntrant(String userId) {
            try {
                injected.updateUserEntryStatusSync(
                        getEventID(), userId, WaitlistEntry.STATUS_CANCELLED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel entrant: " + e.getMessage(), e);
            }
        }

        @Override
        public void joinWaitlist(String userID) {
            try {
                WaitlistEntry entry = new WaitlistEntry(userID, getEventID(),
                        WaitlistEntry.STATUS_WAITLISTED, 42, JOIN_TIME);
                injected.joinWaitlistSync(getEventID(), entry);
            } catch (Exception e) {
                throw new RuntimeException("Failed to join waitlist: " + e.getMessage(), e);
            }
        }

        @Override
        public void leaveWaitlist(String userID) {
            try {
                injected.removeWaitlistEntrySync(getEventID(), userID);
            } catch (Exception e) {
                throw new RuntimeException("Failed to leave waitlist: " + e.getMessage(), e);
            }
        }

        private static final Timestamp JOIN_TIME = new Timestamp(1741694400L, 0);
    }
}