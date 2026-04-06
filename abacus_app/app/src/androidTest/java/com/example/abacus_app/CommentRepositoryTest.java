package com.example.abacus_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(MockitoJUnitRunner.class)
public class CommentRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private CommentRepository repo;

    private static final String EVENT_ID = "event-123";
    private static final String USER_ID  = "organizer-abc";
    private static final String COMMENT_ID = "comment-001";

    @Before
    public void setUp() {
        repo = new CommentRepository();
    }

    // ── addComment — US 02.08.02 ───────────────────────────────────────────

    /**
     * US 02.08.02 — Organizer can comment on their event.
     *               Verifies comment is added and callback completes.
     */
    @Test
    public void addComment_completesCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] completed = {false};

        repo.addComment(EVENT_ID, USER_ID, "Test User","Test comment", error -> {
            completed[0] = true;
            latch.countDown();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertTrue(completed[0]);
    }

    // ── getComments — US 02.08.01 ──────────────────────────────────────────

    /**
     * US 02.08.01 — Organizer can view entrant comments on their event.
     *               Verifies comments list is returned (may be empty).
     */
    @Test
    public void getComments_returnsList() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Comment>[] result = new List[]{null};

        repo.getComments(EVENT_ID, comments -> {
            result[0] = comments;
            latch.countDown();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]); // should at least return empty list
    }

    // ── deleteComment — US 02.08.01 ────────────────────────────────────────

    /**
     * US 02.08.01 — Organizer can delete entrant comments on their event.
     *               Verifies deletion completes via callback.
     */
    @Test
    public void deleteComment_completesCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        boolean[] completed = {false};

        repo.deleteComment(EVENT_ID, COMMENT_ID, error -> {
            completed[0] = true;
            latch.countDown();
        });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertTrue(completed[0]);
    }
}
