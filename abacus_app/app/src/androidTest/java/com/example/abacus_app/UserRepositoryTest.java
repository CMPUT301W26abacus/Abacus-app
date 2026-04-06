package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserRepository.
 *
 * Covers:
 *   US 01.07.01 — getCurrentUserId: returns device UUID via callback
 *   US 01.02.01 — saveProfile: creates new profile in Firestore
 *   US 01.02.02 — getProfile / saveProfile: reads & updates existing profile
 *   US 01.02.04 — deleteProfile: soft-deletes profile in Firestore
 *
 * All Firestore calls are mocked — no network required.
 * CountDownLatch (3 s timeout) is used because UserRepository dispatches
 * work to a background executor and returns results via callback on the main thread.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserRepositoryTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock UserLocalDataSource  mockLocal;
    @Mock UserRemoteDataSource mockRemote;

    private UserRepository repo;

    // Realistic test users
    private static final String ALICE_UUID  = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String BOB_UUID    = "550e8400-e29b-41d4-a716-446655440000";

    @Before
    public void setUp() {
        repo = new UserRepository(mockLocal, mockRemote);
    }

    // ── getCurrentUserId — US 01.07.01 ───────────────────────────────────────

    /**
     * US 01.07.01 — Existing UUID is returned via callback.
     *               Simulates returning user on any launch after the first.
     */
    @Test
    public void getCurrentUserId_uuidExists_deliveredViaCallback() throws InterruptedException {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);

        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {null};

        repo.getCurrentUserId(uuid -> { result[0] = uuid; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertEquals(ALICE_UUID, result[0]);
    }

    /**
     * US 01.07.01 — Returns null when no UUID is stored yet (fresh install).
     *               Caller is responsible for generating a new UUID.
     */
    @Test
    public void getCurrentUserId_noUUID_returnsNullViaCallback() throws InterruptedException {
        when(mockLocal.getUUIDSync()).thenReturn(null);

        CountDownLatch latch = new CountDownLatch(1);
        String[] result = {"SENTINEL"};

        repo.getCurrentUserId(uuid -> { result[0] = uuid; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull(result[0]);
    }

    // ── getProfile — US 01.02.02 ─────────────────────────────────────────────

    /**
     * US 01.02.02 — Returns the full user object when the Firestore document exists.
     */
    @Test
    public void getProfile_documentExists_returnsPopulatedUser() throws Exception {
        User alice = new User(ALICE_UUID, "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        alice.setPhone("780-492-3111");

        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        when(mockRemote.getUserSync(ALICE_UUID)).thenReturn(alice);

        CountDownLatch latch = new CountDownLatch(1);
        User[] result = {null};

        repo.getProfile(user -> { result[0] = user; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertEquals("Alice Smith",       result[0].getName());
        assertEquals("alice@ualberta.ca", result[0].getEmail());
        assertEquals("780-492-3111",      result[0].getPhone());
    }

    /**
     * US 01.02.02 — Returns null when Firestore has no document for this UUID.
     *               This happens for users whose accounts were deleted server-side.
     */
    @Test
    public void getProfile_noDocument_returnsNull() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(BOB_UUID);
        when(mockRemote.getUserSync(BOB_UUID)).thenReturn(null);

        CountDownLatch latch = new CountDownLatch(1);
        User[] result = {new User()}; // sentinel — should become null

        repo.getProfile(user -> { result[0] = user; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull(result[0]);
    }

    /**
     * US 01.02.02 — Returns null (not a crash) when no UUID is saved locally.
     */
    @Test
    public void getProfile_noLocalUUID_returnsNull() throws InterruptedException {
        when(mockLocal.getUUIDSync()).thenReturn(null);

        CountDownLatch latch = new CountDownLatch(1);
        User[] result = {new User()};

        repo.getProfile(user -> { result[0] = user; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull(result[0]);
    }

    /**
     * US 01.02.02 — Firestore exception is caught gracefully; callback receives null.
     */
    @Test
    public void getProfile_remoteThrows_callbackReceivesNull() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        when(mockRemote.getUserSync(ALICE_UUID)).thenThrow(new RuntimeException("Firestore offline"));

        CountDownLatch latch = new CountDownLatch(1);
        User[] result = {new User()};

        repo.getProfile(user -> { result[0] = user; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull(result[0]);
    }

    // ── saveProfile — US 01.02.01 / US 01.02.02 ──────────────────────────────

    /**
     * US 01.02.01 — Creating a profile writes name, email, phone to Firestore.
     */
    @Test
    public void saveProfile_newProfile_callsRemoteUpdateWithCorrectData() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doNothing().when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> data = new HashMap<>();
        data.put("name",  "Alice Smith");
        data.put("email", "alice@ualberta.ca");
        data.put("phone", "780-492-3111");

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {new Exception("sentinel")};

        repo.saveProfile(data, e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull("Expected no error", error[0]);
        verify(mockRemote).updateUserSync(ALICE_UUID, data);
    }

    /**
     * US 01.02.02 — Updating a profile with only changed fields merges correctly.
     */
    @Test
    public void saveProfile_updateExistingProfile_writesChangedFields() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(BOB_UUID);
        doNothing().when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> update = new HashMap<>();
        update.put("name",  "Robert Johnson");
        update.put("phone", "780-555-9876");
        // email intentionally omitted — partial update

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {new Exception("sentinel")};

        repo.saveProfile(update, e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull("Expected no error", error[0]);
        verify(mockRemote).updateUserSync(eq(BOB_UUID), eq(update));
    }

    /**
     * US 01.02.01 — saveProfile delivers the Firestore exception via callback
     *               rather than crashing the app.
     */
    @Test
    public void saveProfile_remoteThrows_callbackReceivesException() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doThrow(new RuntimeException("Network error"))
                .when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice Smith");

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {null};

        repo.saveProfile(data, e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull("Expected an error", error[0]);
    }

    // ── deleteProfile — US 01.02.04 ──────────────────────────────────────────

    /**
     * US 01.02.04 — Deleting a profile calls deleteUserSync on the remote source.
     *               The remote method performs a soft-delete (sets isDeleted=true).
     */
    @Test
    public void deleteProfile_success_callsRemoteDelete() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doNothing().when(mockRemote).deleteUserSync(ALICE_UUID);

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {new Exception("sentinel")};

        repo.deleteProfile(e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull("Expected no error", error[0]);
        verify(mockRemote).deleteUserSync(ALICE_UUID);
    }

    /**
     * US 01.02.04 — Delete failure is delivered via callback without crashing.
     */
    @Test
    public void deleteProfile_remoteThrows_callbackReceivesException() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(BOB_UUID);
        doThrow(new RuntimeException("Permission denied"))
                .when(mockRemote).deleteUserSync(BOB_UUID);

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {null};

        repo.deleteProfile(e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNotNull("Expected an error", error[0]);
    }

    /**
     * US 01.02.04 — Delete does nothing (no remote call) when UUID is missing.
     *               Should still invoke the callback cleanly.
     */
    @Test
    public void deleteProfile_noLocalUUID_noRemoteCallMade() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(null);

        CountDownLatch latch = new CountDownLatch(1);
        latch.countDown(); // pre-release since we just need to verify no remote call

        repo.deleteProfile(e -> {});

        // Give executor time to run
        Thread.sleep(500);
        verify(mockRemote, never()).deleteUserSync(anyString());
    }

    // ── clearLocalSession ────────────────────────────────────────────────────

    /**
     * clearLocalSession clears the device UUID from local storage so the next
     * app launch starts with a fresh anonymous identity.
     */
    @Test
    public void clearLocalSession_clearsDeviceIdFromLocalStorage() {
        repo.clearLocalSession();
        verify(mockLocal).clearDeviceId();
    }

    // ── saveProfile — creates a UUID when none exists (registration flow) ─────

    /**
     * US 01.02.01 — saveProfile generates a UUID via getOrCreateUUID() when
     * none is stored, so profile save works even before initializeUser() runs.
     */
    @Test
    public void saveProfile_noLocalUUID_generatesUUIDAndSaves() throws Exception {
        // getUUIDSync returns null first (no UUID stored), then saveUUIDSync is called,
        // then getUUIDSync returns the new UUID — simulate this with thenReturn chaining.
        when(mockLocal.getUUIDSync())
                .thenReturn(null)          // first call: no UUID stored
                .thenReturn(ALICE_UUID);   // after saveUUIDSync is called
        doNothing().when(mockLocal).saveUUIDSync(anyString());
        doNothing().when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice Smith");

        CountDownLatch latch = new CountDownLatch(1);
        Exception[] error = {new Exception("sentinel")};

        repo.saveProfile(data, e -> { error[0] = e; latch.countDown(); });

        assertTrue("callback timed out", latch.await(3, TimeUnit.SECONDS));
        assertNull("Expected no error", error[0]);
        // A UUID was written to local storage
        verify(mockLocal).saveUUIDSync(anyString());
    }
}