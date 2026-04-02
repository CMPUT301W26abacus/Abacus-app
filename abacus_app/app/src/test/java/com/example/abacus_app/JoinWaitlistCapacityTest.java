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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for waitlist capacity enforcement and the location-before-callback bug fix.
 *
 * <p>Covers:
 * <ul>
 *   <li>US 01.05.01 — Join waitlist: success when under capacity.</li>
 *   <li>US 01.05.02 — Join waitlist: rejected when at capacity.</li>
 *   <li>Duplicate join prevention (OWASP M1 — Improper Input Validation).</li>
 *   <li>Regression: location coordinates must be set on the entry before the
 *       Firestore write (previous bug: lat/lng were set after the callback fired).</li>
 * </ul>
 *
 * <p>Uses Robolectric for {@code Handler/Looper} and Mockito to isolate Firestore.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class JoinWaitlistCapacityTest {

    @Mock RegistrationRemoteDataSource mockRegistrationRDS;
    @Mock UserRemoteDataSource         mockUserRDS;
    @Mock EventRemoteDataSource        mockEventRDS;

    private RegistrationRepository repo;

    private static final String EVENT_ID = "evt-capacity-test";
    private static final String USER_ID  = "user-abc";

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
    }

    // ── Happy path — US 01.05.01 ──────────────────────────────────────────────

    /**
     * US 01.05.01 — Join succeeds when the waitlist is not full.
     * The atomic join must not throw, and the callback receives {@code null} (success).
     */
    @Test
    public void join_succeedsWhenUnderCapacity() throws Exception {
        // joinWaitlistAtomicSync does not throw → success
        doNothing().when(mockRegistrationRDS).joinWaitlistAtomicSync(eq(EVENT_ID), any());

        Exception[] error = {new Exception("sentinel")};
        repo.joinWaitlist(USER_ID, EVENT_ID, null, e -> error[0] = e);

        drainUntil(() -> !"sentinel".equals(getMsg(error[0])));
        assertNull("Expected no error for under-capacity join", error[0]);
        verify(mockRegistrationRDS).joinWaitlistAtomicSync(eq(EVENT_ID), any());
    }

    // ── Capacity enforcement — US 01.05.02 ───────────────────────────────────

    /**
     * US 01.05.02 — Join is rejected when the waitlist is full.
     * The atomic transaction throws {@link IllegalStateException}; the callback must
     * receive that exception.
     */
    @Test
    public void join_failsWhenAtCapacity() throws Exception {
        doThrow(new IllegalStateException("Waitlist is full."))
                .when(mockRegistrationRDS).joinWaitlistAtomicSync(eq(EVENT_ID), any());

        Exception[] error = {null};
        boolean[] done = {false};
        repo.joinWaitlist(USER_ID, EVENT_ID, null, e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Expected error when at capacity", error[0]);
        assertTrue(error[0] instanceof IllegalStateException);
        assertTrue(error[0].getMessage().contains("full"));
    }

    // ── Duplicate join prevention ─────────────────────────────────────────────

    /**
     * A user who is already on the waitlist must be rejected.
     * The atomic transaction throws {@link IllegalArgumentException}; the callback
     * must receive that exception.
     */
    @Test
    public void join_failsWhenAlreadyOnWaitlist() throws Exception {
        doThrow(new IllegalArgumentException("User is already on the waitlist."))
                .when(mockRegistrationRDS).joinWaitlistAtomicSync(eq(EVENT_ID), any());

        Exception[] error = {null};
        boolean[] done = {false};
        repo.joinWaitlist(USER_ID, EVENT_ID, null, e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Expected error for duplicate join", error[0]);
        assertTrue(error[0] instanceof IllegalArgumentException);
    }

    // ── Location regression test ──────────────────────────────────────────────

    /**
     * Regression: latitude and longitude must be attached to the {@link WaitlistEntry}
     * <em>before</em> {@code joinWaitlistAtomicSync} is called.
     *
     * <p>Previous bug: location was set after {@code callback.onComplete(null)}, so
     * the Firestore document was written without coordinates.
     */
    @Test
    public void join_locationIsSetOnEntryBeforeFirestoreWrite() throws Exception {
        android.location.Location fakeLocation = mock(android.location.Location.class);
        when(fakeLocation.getLatitude()).thenReturn(53.5461);
        when(fakeLocation.getLongitude()).thenReturn(-113.4938);

        AtomicReference<WaitlistEntry> capturedEntry = new AtomicReference<>();

        doAnswer(inv -> {
            capturedEntry.set((WaitlistEntry) inv.getArgument(1));
            return null;
        }).when(mockRegistrationRDS).joinWaitlistAtomicSync(eq(EVENT_ID), any());

        Exception[] error = {new Exception("sentinel")};
        repo.joinWaitlist(USER_ID, EVENT_ID, fakeLocation, e -> error[0] = e);
        drainUntil(() -> !"sentinel".equals(getMsg(error[0])));

        assertNull("Expected no error", error[0]);
        assertNotNull("Entry should have been passed to joinWaitlistAtomicSync", capturedEntry.get());
        assertEquals(53.5461, capturedEntry.get().getLatitude(), 0.0001);
        assertEquals(-113.4938, capturedEntry.get().getLongitude(), 0.0001);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getMsg(Exception e) { return e != null ? e.getMessage() : null; }

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
