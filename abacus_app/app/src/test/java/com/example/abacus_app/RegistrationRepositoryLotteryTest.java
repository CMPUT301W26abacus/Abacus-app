package com.example.abacus_app;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the lottery algorithm in {@link RegistrationRepository#runLottery}.
 *
 * <p>Covers:
 * <ul>
 *   <li>US 02.02.01 — Organizer runs lottery: selects exactly capacity entries.</li>
 *   <li>US 02.02.02 — Lottery when list smaller than capacity: selects all.</li>
 *   <li>Correctness invariant: chosen and not-chosen sets are disjoint and exhaustive.</li>
 *   <li>Guard: cannot re-run a lottery that was already drawn.</li>
 *   <li>Guard: cannot run lottery on an empty waitlist.</li>
 * </ul>
 *
 * <p>Uses Robolectric for {@code Handler/Looper} support and Mockito to isolate
 * Firestore calls.  No network or device required.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class RegistrationRepositoryLotteryTest {

    @Mock RegistrationRemoteDataSource mockRegistrationRDS;
    @Mock UserRemoteDataSource         mockUserRDS;
    @Mock EventRemoteDataSource        mockEventRDS;

    private RegistrationRepository repo;

    private static final String EVENT_ID = "evt-lottery-test";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        if (FirebaseApp.getApps(RuntimeEnvironment.getApplication()).isEmpty()) {
            FirebaseOptions opts = new FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("fake-api-key")
                    .setProjectId("fake-project")
                    .build();
            FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(), opts);
        }

        repo = new RegistrationRepository(mockRegistrationRDS, mockUserRDS, mockEventRDS);

        // updateEvent is a void method (throws checked) — stub to do nothing by default
        doNothing().when(mockEventRDS).updateEvent(any(Event.class));
    }

    // ── Lottery selection — US 02.02.01 ──────────────────────────────────────

    /**
     * US 02.02.01 — When the waitlist is larger than capacity, exactly
     * {@code eventCapacity} entries receive {@code STATUS_INVITED}.
     */
    @Test
    public void lottery_selectsExactlyCapacity_whenListLarger() throws Exception {
        int capacity = 5;
        Event event = fakeEvent(capacity, /* lotteryDrawn= */ false);
        ArrayList<WaitlistEntry> entries = makeEntries(20);

        when(mockEventRDS.getEventById(EVENT_ID)).thenReturn(event);
        when(mockRegistrationRDS.getWaitlistSizeSync(EVENT_ID)).thenReturn(20);
        when(mockRegistrationRDS.getEntriesWithStatusSync(EVENT_ID, WaitlistEntry.STATUS_WAITLISTED))
                .thenReturn(entries);

        Exception[] error = {new Exception("sentinel")};
        repo.runLottery(EVENT_ID, e -> error[0] = e);
        drainUntil(() -> error[0] == null || !(error[0].getMessage().equals("sentinel")));

        assertNull("Expected no error", error[0]);
        verify(mockRegistrationRDS, times(capacity))
                .updateUserEntryStatusSync(eq(EVENT_ID), anyString(), eq(WaitlistEntry.STATUS_INVITED));
    }

    /**
     * US 02.02.02 — When the waitlist is smaller than capacity, all entries
     * receive {@code STATUS_INVITED}.
     */
    @Test
    public void lottery_selectsAll_whenListSmallerThanCapacity() throws Exception {
        int capacity = 50;
        int listSize = 7;
        Event event = fakeEvent(capacity, false);
        ArrayList<WaitlistEntry> entries = makeEntries(listSize);

        when(mockEventRDS.getEventById(EVENT_ID)).thenReturn(event);
        when(mockRegistrationRDS.getWaitlistSizeSync(EVENT_ID)).thenReturn(listSize);
        when(mockRegistrationRDS.getEntriesWithStatusSync(EVENT_ID, WaitlistEntry.STATUS_WAITLISTED))
                .thenReturn(entries);

        Exception[] error = {new Exception("sentinel")};
        repo.runLottery(EVENT_ID, e -> error[0] = e);
        drainUntil(() -> !"sentinel".equals(getMsg(error[0])));

        assertNull("Expected no error", error[0]);
        verify(mockRegistrationRDS, times(listSize))
                .updateUserEntryStatusSync(eq(EVENT_ID), anyString(), eq(WaitlistEntry.STATUS_INVITED));
    }

    /**
     * Verifies that the lottery sorts by lottery number and invites the lowest N
     * entries.  The entries with the 3 lowest numbers must be the ones invited.
     */
    @Test
    public void lottery_invitesLowestLotteryNumbers() throws Exception {
        Event event = fakeEvent(3, false);

        // Entries with known lottery numbers; lowest 3 are users 10, 20, 30
        ArrayList<WaitlistEntry> entries = new ArrayList<>();
        entries.add(new WaitlistEntry("user10", EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, 10, 1000L));
        entries.add(new WaitlistEntry("user99", EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, 99, 1000L));
        entries.add(new WaitlistEntry("user20", EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, 20, 1000L));
        entries.add(new WaitlistEntry("user30", EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, 30, 1000L));
        entries.add(new WaitlistEntry("user88", EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, 88, 1000L));

        when(mockEventRDS.getEventById(EVENT_ID)).thenReturn(event);
        when(mockRegistrationRDS.getWaitlistSizeSync(EVENT_ID)).thenReturn(5);
        when(mockRegistrationRDS.getEntriesWithStatusSync(EVENT_ID, WaitlistEntry.STATUS_WAITLISTED))
                .thenReturn(entries);

        Set<String> invited = new HashSet<>();
        doAnswer(inv -> {
            invited.add((String) inv.getArgument(1));
            return null;
        }).when(mockRegistrationRDS)
          .updateUserEntryStatusSync(eq(EVENT_ID), anyString(), eq(WaitlistEntry.STATUS_INVITED));

        Exception[] error = {new Exception("sentinel")};
        repo.runLottery(EVENT_ID, e -> error[0] = e);
        drainUntil(() -> !"sentinel".equals(getMsg(error[0])));

        assertNull(error[0]);
        assertTrue("user10 should be invited", invited.contains("user10"));
        assertTrue("user20 should be invited", invited.contains("user20"));
        assertTrue("user30 should be invited", invited.contains("user30"));
        assertFalse("user88 should NOT be invited", invited.contains("user88"));
        assertFalse("user99 should NOT be invited", invited.contains("user99"));
    }

    // ── Guard conditions ──────────────────────────────────────────────────────

    /**
     * Running the lottery a second time must fail with an error callback.
     */
    @Test
    public void lottery_failsWithError_whenAlreadyDrawn() throws Exception {
        Event event = fakeEvent(10, /* lotteryDrawn= */ true);
        when(mockEventRDS.getEventById(EVENT_ID)).thenReturn(event);
        when(mockRegistrationRDS.getWaitlistSizeSync(EVENT_ID)).thenReturn(5);

        Exception[] error = {null};
        boolean[] done = {false};
        repo.runLottery(EVENT_ID, e -> { error[0] = e; done[0] = true; });
        drainUntil(() -> done[0]);

        assertNotNull("Expected error for already-drawn lottery", error[0]);
        assertTrue(error[0] instanceof IllegalStateException);
        verify(mockRegistrationRDS, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    /**
     * Running the lottery on an empty waitlist must fail with an error callback.
     */
    @Test
    public void lottery_failsWithError_whenWaitlistEmpty() throws Exception {
        Event event = fakeEvent(10, false);
        when(mockEventRDS.getEventById(EVENT_ID)).thenReturn(event);
        when(mockRegistrationRDS.getWaitlistSizeSync(EVENT_ID)).thenReturn(0);

        Exception[] error = {null};
        boolean[] done = {false};
        repo.runLottery(EVENT_ID, e -> { error[0] = e; done[0] = true; });
        drainUntil(() -> done[0]);

        assertNotNull("Expected error for empty waitlist", error[0]);
        assertTrue(error[0] instanceof IllegalStateException);
        verify(mockRegistrationRDS, never())
                .updateUserEntryStatusSync(anyString(), anyString(), anyString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Event fakeEvent(int capacity, boolean lotteryDrawn) {
        Event e = new Event();
        e.setEventId(EVENT_ID);
        e.setEventCapacity(capacity);
        e.setLotteryDrawn(lotteryDrawn);
        return e;
    }

    private ArrayList<WaitlistEntry> makeEntries(int count) {
        ArrayList<WaitlistEntry> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new WaitlistEntry(
                    "user" + i, EVENT_ID, WaitlistEntry.STATUS_WAITLISTED, i * 100, 1000L));
        }
        return list;
    }

    private String getMsg(Exception e) {
        return e != null ? e.getMessage() : null;
    }

    private void drainUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.get() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertTrue("callback timed out", condition.get());
    }

    @FunctionalInterface
    interface BooleanSupplier { boolean get(); }
}
