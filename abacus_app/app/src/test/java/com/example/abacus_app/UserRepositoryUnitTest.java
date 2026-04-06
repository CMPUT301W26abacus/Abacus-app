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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserRepository}.
 *
 * Covers:
 *   US 01.07.01 — getCurrentUserId: returns device UUID via callback
 *   US 01.02.01 — saveProfile: creates/updates profile in Firestore
 *   US 01.02.02 — getProfile: fetches and returns existing profile
 *   US 01.02.04 — deleteProfile: soft-deletes the user document
 *
 * Runs on the JVM via Robolectric (required for {@code Handler/Looper}).
 * Firebase is initialised from the Robolectric application context so that
 * {@code FirebaseAuth.getInstance()} calls don't crash.
 * All Firestore and local-storage calls are mocked — no network required.
 *
 * Threading note: {@code UserRepository} posts callbacks to the main looper.
 * Tests use {@link ShadowLooper#idle()} inside a time-bounded polling loop to
 * drain the looper without blocking the main thread with {@code latch.await()}.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class UserRepositoryUnitTest {

    @Mock UserLocalDataSource  mockLocal;
    @Mock UserRemoteDataSource mockRemote;

    private UserRepository repo;

    private static final String ALICE_UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String BOB_UUID   = "550e8400-e29b-41d4-a716-446655440000";

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Firebase must be initialised before any FirebaseAuth call.
        // Use explicit FirebaseOptions to avoid reading google-services.json resources
        // (which are unavailable in the Robolectric sandbox with Config.NONE).
        if (FirebaseApp.getApps(RuntimeEnvironment.getApplication()).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("fake-api-key")
                    .setProjectId("fake-project")
                    .build();
            FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(), options);
        }

        repo = new UserRepository(mockLocal, mockRemote);
    }

    // ── getCurrentUserId — US 01.07.01 ───────────────────────────────────────

    /**
     * US 01.07.01 — Existing UUID is delivered via callback (subsequent launches).
     */
    @Test
    public void getCurrentUserId_uuidExists_deliveredViaCallback() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);

        String[] result = {null};
        repo.getCurrentUserId(uuid -> result[0] = uuid);

        drainUntil(() -> result[0] != null);
        assertEquals(ALICE_UUID, result[0]);
    }

    /**
     * US 01.07.01 — Fresh install: when no UUID is stored, the repository generates
     * a new one (using ANDROID_ID or random UUID) and returns it via callback.
     * The result must be non-null.
     */
    @Test
    public void getCurrentUserId_noUUIDStored_generatesAndReturnsNewUUID() throws Exception {
        // No UUID in local storage; ANDROID_ID mock returns null (emulator behaviour)
        when(mockLocal.getUUIDSync()).thenReturn(null);
        when(mockLocal.getStableDeviceID()).thenReturn(null);

        String[] result = {null};
        boolean[] done  = {false};
        repo.getCurrentUserId(uuid -> { result[0] = uuid; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Generated UUID must not be null", result[0]);
        assertFalse("Generated UUID must not be empty", result[0].isEmpty());
    }

    /**
     * US 01.07.01 — When ANDROID_ID is available, it is used as the stable UUID
     * on first launch (survives reinstall).
     */
    @Test
    public void getCurrentUserId_androidIdAvailable_usesAndroidId() throws Exception {
        String androidId = "abcdef1234567890";
        when(mockLocal.getUUIDSync()).thenReturn(null); // not yet stored
        when(mockLocal.getStableDeviceID()).thenReturn(androidId);

        String[] result = {null};
        boolean[] done  = {false};
        repo.getCurrentUserId(uuid -> { result[0] = uuid; done[0] = true; });

        drainUntil(() -> done[0]);
        assertEquals(androidId, result[0]);
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

        User[] result = {null};
        boolean[] done = {false};

        repo.getProfile(user -> { result[0] = user; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull(result[0]);
        assertEquals("Alice Smith",       result[0].getName());
        assertEquals("alice@ualberta.ca", result[0].getEmail());
        assertEquals("780-492-3111",      result[0].getPhone());
    }

    /**
     * US 01.02.02 — Returns null when no document exists for the UUID.
     */
    @Test
    public void getProfile_noDocument_returnsNull() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(BOB_UUID);
        when(mockRemote.getUserSync(BOB_UUID)).thenReturn(null);

        boolean[] done = {false};
        User[] result = {new User()};

        repo.getProfile(user -> { result[0] = user; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull(result[0]);
    }

    /**
     * US 01.02.02 — Returns null (no crash) when no UUID is stored locally.
     */
    @Test
    public void getProfile_noLocalUUID_returnsNull() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(null);

        boolean[] done = {false};
        User[] result = {new User()};

        repo.getProfile(user -> { result[0] = user; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull(result[0]);
    }

    /**
     * US 01.02.02 — Firestore exception is caught; callback receives null.
     */
    @Test
    public void getProfile_remoteThrows_callbackReceivesNull() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        when(mockRemote.getUserSync(ALICE_UUID))
                .thenThrow(new RuntimeException("Firestore offline"));

        boolean[] done = {false};
        User[] result = {new User()};

        repo.getProfile(user -> { result[0] = user; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull(result[0]);
    }

    // ── saveProfile — US 01.02.01 / US 01.02.02 ──────────────────────────────

    /**
     * US 01.02.01 — saveProfile writes the correct data to Firestore.
     */
    @Test
    public void saveProfile_validData_callsRemoteUpdate() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doNothing().when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> data = new HashMap<>();
        data.put("name",  "Alice Smith");
        data.put("email", "alice@ualberta.ca");

        boolean[] done = {false};
        Exception[] error = {new Exception("sentinel")};

        repo.saveProfile(data, e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull("Expected no error", error[0]);
        verify(mockRemote).updateUserSync(ALICE_UUID, data);
    }

    /**
     * US 01.02.01 — Firestore exception during saveProfile is delivered via callback.
     */
    @Test
    public void saveProfile_remoteThrows_callbackReceivesException() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doThrow(new RuntimeException("Network error"))
                .when(mockRemote).updateUserSync(anyString(), anyMap());

        boolean[] done = {false};
        Exception[] error = {null};

        repo.saveProfile(new HashMap<>(), e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Expected an error", error[0]);
    }

    // ── deleteProfile — US 01.02.04 ──────────────────────────────────────────

    /**
     * US 01.02.04 — deleteProfile calls deleteUserSync on the remote source.
     * Firebase Auth returns null current user (not signed in) in Robolectric, so
     * the Auth-delete step is a no-op and the callback still receives null (success).
     */
    @Test
    public void deleteProfile_success_callsRemoteDelete() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doNothing().when(mockRemote).deleteUserSync(ALICE_UUID);

        boolean[] done = {false};
        Exception[] error = {new Exception("sentinel")};

        repo.deleteProfile(e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull("Expected no error", error[0]);
        verify(mockRemote).deleteUserSync(ALICE_UUID);
    }

    /**
     * US 01.02.04 — Remote exception during delete is delivered via callback.
     */
    @Test
    public void deleteProfile_remoteThrows_callbackReceivesException() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(BOB_UUID);
        doThrow(new RuntimeException("Permission denied"))
                .when(mockRemote).deleteUserSync(BOB_UUID);

        boolean[] done = {false};
        Exception[] error = {null};

        repo.deleteProfile(e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Expected an error", error[0]);
    }

    /**
     * US 01.02.04 — No remote call is made when no UUID is stored locally.
     */
    @Test
    public void deleteProfile_noLocalUUID_noRemoteCallMade() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(null);

        boolean[] done = {false};
        repo.deleteProfile(e -> done[0] = true);

        drainUntil(() -> done[0]);
        verify(mockRemote, never()).deleteUserSync(anyString());
    }

    // ── savePreferencesAsync ─────────────────────────────────────────────────

    /**
     * savePreferencesAsync wraps the preferences map under the "preferences" key
     * and calls updateUserSync with the correct UUID.
     */
    @Test
    public void savePreferencesAsync_callsUpdateWithCorrectMap() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doNothing().when(mockRemote).updateUserSync(anyString(), anyMap());

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("categories", Arrays.asList("Music", "Sports"));
        prefs.put("locationRangeKm", 25);

        boolean[] done = {false};
        Exception[] error = {new Exception("sentinel")};

        repo.savePreferencesAsync(prefs, e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNull("Expected no error", error[0]);
        verify(mockRemote).updateUserSync(
                eq(ALICE_UUID),
                argThat(map -> map.containsKey("preferences")));
    }

    /**
     * savePreferencesAsync propagates exceptions via the callback.
     */
    @Test
    public void savePreferencesAsync_propagatesError() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        doThrow(new RuntimeException("Firestore offline"))
                .when(mockRemote).updateUserSync(anyString(), anyMap());

        boolean[] done = {false};
        Exception[] error = {null};

        repo.savePreferencesAsync(new HashMap<>(), e -> { error[0] = e; done[0] = true; });

        drainUntil(() -> done[0]);
        assertNotNull("Expected an error", error[0]);
    }

    // ── initializeUser — US 01.07.01 ─────────────────────────────────────────

    /**
     * US 01.07.01 — First launch: when no Firestore document exists for the UUID,
     * {@code createUserSync} must be called to bootstrap the user record.
     */
    @Test
    public void initializeUser_firstLaunch_createsFirestoreDocument() throws Exception {
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        when(mockRemote.getUserSync(ALICE_UUID)).thenReturn(null); // no document yet

        repo.initializeUser();

        // Allow background executor + Firebase anonymous sign-in (no-op in Robolectric)
        // then ensureFirestoreDocumentExists to run
        long deadline = System.currentTimeMillis() + 3_000;
        // Poll until createUserSync is invoked or timeout
        while (System.currentTimeMillis() < deadline) {
            try {
                verify(mockRemote).createUserSync(eq(ALICE_UUID), anyMap());
                return; // verified — test passes
            } catch (org.mockito.exceptions.base.MockitoAssertionError ignored) {
                ShadowLooper.idleMainLooper();
                TimeUnit.MILLISECONDS.sleep(50);
            }
        }
        verify(mockRemote).createUserSync(eq(ALICE_UUID), anyMap());
    }

    /**
     * US 01.07.01 — Subsequent launch: when a Firestore document already exists
     * for the UUID, {@code createUserSync} must NOT be called again.
     */
    @Test
    public void initializeUser_uuidExists_doesNotCreateDuplicateDocument() throws Exception {
        User existing = new User(ALICE_UUID, "alice@ualberta.ca", "Alice", "2026-01-01");
        when(mockLocal.getUUIDSync()).thenReturn(ALICE_UUID);
        when(mockRemote.getUserSync(ALICE_UUID)).thenReturn(existing);

        repo.initializeUser();

        // Wait for executor to drain
        Thread.sleep(500);
        ShadowLooper.idleMainLooper();

        verify(mockRemote, never()).createUserSync(anyString(), anyMap());
    }

    // ── clearLocalSession ────────────────────────────────────────────────────

    /**
     * clearLocalSession removes the local UUID. Firebase signOut is a no-op in
     * Robolectric (not signed in), so the call completes without throwing.
     */
    @Test
    public void clearLocalSession_clearsDeviceId() {
        repo.clearLocalSession();
        verify(mockLocal).clearDeviceId();
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Polls the main looper (via {@link ShadowLooper#idle()}) and sleeps briefly
     * between iterations until {@code condition} is true or 3 seconds elapse.
     * This avoids blocking the main thread with {@code CountDownLatch.await()},
     * which would prevent the looper from draining.
     */
    private void drainUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3_000;
        while (!condition.get() && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            TimeUnit.MILLISECONDS.sleep(50);
        }
        assertTrue("callback timed out", condition.get());
    }

    @FunctionalInterface
    interface BooleanSupplier {
        boolean get();
    }
}
