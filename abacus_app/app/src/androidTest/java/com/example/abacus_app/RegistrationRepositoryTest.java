package com.example.abacus_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class RegistrationRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private RegistrationRepository repo;

    private static final String EVENT_ID = "event-123";
    private static final String USER_ID  = "user-abc";

    @Before
    public void setUp() {
        repo = new RegistrationRepository();

        CountDownLatch latch = new CountDownLatch(1);
            repo.joinWaitlist(USER_ID, EVENT_ID, null,e -> {
                latch.countDown();
            });
    }

    // ── acceptInvitation — US 01.05.02 ───────────────────────────────────────

    /**
     * US 01.05.02 — Entrant accepts invitation to register/sign up when chosen.
     *               Verifies callback completes successfully.
     */
    @Test
    public void acceptInvitation_completesCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] completed = {false};

        repo.acceptInvitation(EVENT_ID, USER_ID, error -> {
            completed[0] = true;
            latch.countDown();
        });
        String[] status = {""};
        repo.getUserEntry(USER_ID, EVENT_ID, entry -> {
            status[0] = entry.getStatus();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertEquals(WaitlistEntry.STATUS_ACCEPTED, status[0]);
    }

    // ── declineInvitation — US 01.05.03 ─────────────────────────────────────

    /**
     * US 01.05.03 — Entrant declines an invitation when chosen.
     *               Verifies callback completes successfully.
     */
    @Test
    public void declineInvitation_completesCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] completed = {false};

        repo.acceptInvitation(EVENT_ID, USER_ID, error -> {
            completed[0] = true;
            latch.countDown();
        });
        String[] status = {""};
        repo.getUserEntry(USER_ID, EVENT_ID, entry -> {
            status[0] = entry.getStatus();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertEquals(WaitlistEntry.STATUS_DECLINED, status[0]);
    }

    // ── drawReplacement — US 01.05.01 ───────────────────────────────────────

    /**
     * US 01.05.01 — When a selected user declines, another entrant
     *               should be chosen from the waitlist.
     *               Verifies that a replacement draw completes and returns a user.
     */
    @Test
    public void drawReplacement_returnsUserFromWaitlist() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        String[] selectedUserId = {null};

        repo.drawReplacement(EVENT_ID, user -> {
            selectedUserId[0] = user.getUserId();
            latch.countDown();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));

        if (selectedUserId[0] != null) {
            assertFalse(selectedUserId[0].isEmpty());
        }
    }

    // ── getWaitlistCount — US 01.05.04 ──────────────────────────────────────

    /**
     * US 01.05.04 — Entrant can see how many users are on the waitlist.
     *               Verifies a non-negative count is returned.
     */
    @Test
    public void getWaitlistCount_returnsNonNegativeValue() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        int[] result = {-1};

        repo.getWaitListSize(EVENT_ID, count -> {
            result[0] = count;
            latch.countDown();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertTrue("count should be >= 0", result[0] >= 0);
    }
}
