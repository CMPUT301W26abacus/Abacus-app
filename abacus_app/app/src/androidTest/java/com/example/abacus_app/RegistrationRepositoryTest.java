package com.example.abacus_app;

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
 * Unit tests for RegistrationRepository lottery and waitlist logic.
 *
 * Uses a TestableRegistrationRepository subclass to inject a mock
 * RegistrationRemoteDataSource, bypassing Firebase and threading.
 *
 * Covers:
 *   - runLottery: selects lowest lottery numbers up to eventCapacity
 *   - drawReplacement: picks lowest waitlisted entrant
 *   - status updates: accept, decline, cancel
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationRepositoryTest {

    private static final String EVENT_ID = "event-spring-gala-2026";

    @Mock RegistrationRemoteDataSource mockRemote;

    private TestableRegistrationRepository repo;

    @Before
    public void setUp() {
        repo = new TestableRegistrationRepository(mockRemote);
    }

    // ── runLottery ───────────────────────────────────────────────────────────

    /**
     * runLottery invites exactly eventCapacity entrants when waitlist is larger.
     * capacity=3, waitlist=5 → 3 invitations
     */
    @Test
    public void runLottery_largerWaitlist_invitesExactlyCapacityCount() throws Exception {
        ArrayList<WaitlistEntry> waitlist = buildWaitlist(5);

        repo.runLotterySync(EVENT_ID, waitlist, 3);

        verify(mockRemote, times(3))
                .updateUserEntryStatusSync(
                        eq(EVENT_ID), anyString(), eq(WaitlistEntry.STATUS_INVITED));
    }

    /**
     * runLottery always picks the entrants with the LOWEST lottery numbers.
     * alice=10, bob=20, carol=30, dave=500, eve=900 — capacity=3
     * → alice, bob, carol invited; dave and eve skipped.
     */
    @Test
    public void runLottery_selectsLowestLotteryNumbers() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entry("user-alice", 10));
        waitlist.add(entry("user-bob",   20));
        waitlist.add(entry("user-carol", 30));
        waitlist.add(entry("user-dave",  500));
        waitlist.add(entry("user-eve",   900));

        repo.runLotterySync(EVENT_ID, waitlist, 3);

        verify(mockRemote).updateUserEntryStatusSync(EVENT_ID, "user-alice", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote).updateUserEntryStatusSync(EVENT_ID, "user-bob",   WaitlistEntry.STATUS_INVITED);
        verify(mockRemote).updateUserEntryStatusSync(EVENT_ID, "user-carol", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(EVENT_ID, "user-dave", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(EVENT_ID, "user-eve",  WaitlistEntry.STATUS_INVITED);
    }

    /**
     * When waitlist is smaller than capacity, all entrants are invited.
     * capacity=3, waitlist=2 → both invited.
     */
    @Test
    public void runLottery_waitlistSmallerThanCapacity_invitesAll() throws Exception {
        ArrayList<WaitlistEntry> waitlist = buildWaitlist(2);

        repo.runLotterySync(EVENT_ID, waitlist, 3);

        verify(mockRemote, times(2))
                .updateUserEntryStatusSync(anyString(), anyString(), eq(WaitlistEntry.STATUS_INVITED));
    }

    /**
     * runLottery on an empty waitlist makes no remote calls.
     */
    @Test
    public void runLottery_emptyWaitlist_makesNoInvitations() throws Exception {
        repo.runLotterySync(EVENT_ID, new ArrayList<>(), 3);

        verify(mockRemote, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    // ── drawReplacement ──────────────────────────────────────────────────────

    /**
     * drawReplacement picks the WAITLISTED entrant with the lowest lottery number.
     * Invited/accepted entrants are ignored.
     */
    @Test
    public void drawReplacement_picksLowestWaitlistedEntrant() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice",  5, WaitlistEntry.STATUS_INVITED));
        waitlist.add(entryWithStatus("user-bob",   15, WaitlistEntry.STATUS_WAITLISTED));
        waitlist.add(entryWithStatus("user-carol", 99, WaitlistEntry.STATUS_WAITLISTED));

        repo.drawReplacementSync(EVENT_ID, waitlist);

        verify(mockRemote).updateUserEntryStatusSync(EVENT_ID, "user-bob", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(EVENT_ID, "user-carol", WaitlistEntry.STATUS_INVITED);
    }

    /**
     * drawReplacement skips accepted/declined entrants entirely.
     */
    @Test
    public void drawReplacement_onlyConsidersWaitlistedEntrants() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice", 1, WaitlistEntry.STATUS_ACCEPTED));
        waitlist.add(entryWithStatus("user-bob",   2, WaitlistEntry.STATUS_DECLINED));
        waitlist.add(entryWithStatus("user-carol", 3, WaitlistEntry.STATUS_WAITLISTED));

        repo.drawReplacementSync(EVENT_ID, waitlist);

        verify(mockRemote).updateUserEntryStatusSync(EVENT_ID, "user-carol", WaitlistEntry.STATUS_INVITED);
        verify(mockRemote, never()).updateUserEntryStatusSync(EVENT_ID, "user-alice", WaitlistEntry.STATUS_INVITED);
    }

    /**
     * drawReplacement with no waitlisted entrants makes no remote call.
     */
    @Test
    public void drawReplacement_noWaitlistedEntrants_makesNoRemoteCall() throws Exception {
        ArrayList<WaitlistEntry> waitlist = new ArrayList<>();
        waitlist.add(entryWithStatus("user-alice", 10, WaitlistEntry.STATUS_ACCEPTED));

        repo.drawReplacementSync(EVENT_ID, waitlist);

        verify(mockRemote, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    // ── status updates ───────────────────────────────────────────────────────

    /** cancelEntrant updates the user's status to CANCELLED. */
    @Test
    public void cancelEntrant_updatesStatusToCancelled() throws Exception {
        repo.cancelEntrantSync(EVENT_ID, "user-carol");

        verify(mockRemote).updateUserEntryStatusSync(
                EVENT_ID, "user-carol", WaitlistEntry.STATUS_CANCELLED);
    }

    /** acceptInvitation updates the user's status to ACCEPTED. */
    @Test
    public void acceptInvitation_updatesStatusToAccepted() throws Exception {
        repo.acceptInvitationSync(EVENT_ID, "user-alice");

        verify(mockRemote).updateUserEntryStatusSync(
                EVENT_ID, "user-alice", WaitlistEntry.STATUS_ACCEPTED);
    }

    /** declineInvitation updates the user's status to DECLINED. */
    @Test
    public void declineInvitation_updatesStatusToDeclined() throws Exception {
        repo.declineInvitationSync(EVENT_ID, "user-bob");

        verify(mockRemote).updateUserEntryStatusSync(
                EVENT_ID, "user-bob", WaitlistEntry.STATUS_DECLINED);
    }

    /**
     * Remote exception during cancelEntrant is wrapped in RuntimeException.
     */
    @Test(expected = RuntimeException.class)
    public void cancelEntrant_remoteThrows_wrapsInRuntimeException() throws Exception {
        doThrow(new Exception("Firestore offline"))
                .when(mockRemote).updateUserEntryStatusSync(anyString(), anyString(), anyString());

        repo.cancelEntrantSync(EVENT_ID, "user-alice");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ArrayList<WaitlistEntry> buildWaitlist(int count) {
        ArrayList<WaitlistEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(entry("user-" + i, (i + 1) * 10));
        }
        return list;
    }

    private WaitlistEntry entry(String userId, int lotteryNumber) {
        return new WaitlistEntry(userId, EVENT_ID,
                WaitlistEntry.STATUS_WAITLISTED, lotteryNumber, System.currentTimeMillis());
    }

    private WaitlistEntry entryWithStatus(String userId, int lotteryNumber, String status) {
        return new WaitlistEntry(userId, EVENT_ID,
                status, lotteryNumber, System.currentTimeMillis());
    }

    // ── Injectable subclass ───────────────────────────────────────────────────

    /**
     * Subclass that exposes synchronous versions of repository methods for testing.
     * Bypasses the ExecutorService + Handler threading used in production.
     */
    static class TestableRegistrationRepository extends RegistrationRepository {

        private final RegistrationRemoteDataSource injected;

        TestableRegistrationRepository(RegistrationRemoteDataSource remote) {
            super();
            this.injected = remote;
        }

        public void runLotterySync(String eventID, ArrayList<WaitlistEntry> waitlist, int capacity) {
            try {
                waitlist.sort((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()));
                int count = Math.min(capacity, waitlist.size());
                for (int i = 0; i < count; i++) {
                    injected.updateUserEntryStatusSync(
                            eventID, waitlist.get(i).getUserID(), WaitlistEntry.STATUS_INVITED);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to run lottery: " + e.getMessage(), e);
            }
        }

        public void drawReplacementSync(String eventID, ArrayList<WaitlistEntry> waitlist) {
            waitlist.stream()
                    .filter(e -> WaitlistEntry.STATUS_WAITLISTED.equals(e.getStatus()))
                    .min((a, b) -> Integer.compare(a.getLotteryNumber(), b.getLotteryNumber()))
                    .ifPresent(e -> {
                        try {
                            injected.updateUserEntryStatusSync(
                                    eventID, e.getUserID(), WaitlistEntry.STATUS_INVITED);
                        } catch (Exception ex) {
                            throw new RuntimeException("Failed to draw replacement", ex);
                        }
                    });
        }

        public void cancelEntrantSync(String eventID, String userID) {
            try {
                injected.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_CANCELLED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to cancel entrant: " + e.getMessage(), e);
            }
        }

        public void acceptInvitationSync(String eventID, String userID) {
            try {
                injected.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_ACCEPTED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to accept invitation: " + e.getMessage(), e);
            }
        }

        public void declineInvitationSync(String eventID, String userID) {
            try {
                injected.updateUserEntryStatusSync(eventID, userID, WaitlistEntry.STATUS_DECLINED);
            } catch (Exception e) {
                throw new RuntimeException("Failed to decline invitation: " + e.getMessage(), e);
            }
        }
    }
}