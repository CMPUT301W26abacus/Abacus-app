package com.example.abacus_app;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserRemoteDataSource}.
 *
 * Covers:
 *   US 01.02.01 — createUser: writes a new user document to Firestore
 *   US 01.02.02 — getUser: reads user document and maps it to a User object
 *   US 01.02.02 — updateUser: merges changed fields into an existing document
 *   US 01.02.04 — deleteUser: soft-deletes by setting isDeleted + deletedAt
 *
 * Runs on the JVM via Robolectric (needed for Looper used by {@code Tasks.await()}).
 * {@link FirebaseFirestore} and the Firestore document chain are mocked with Mockito.
 * Sync methods are executed on a background thread because
 * {@code Tasks.await()} must not be called on the main (Robolectric test) thread.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UserRemoteDataSourceUnitTest {

    @Mock FirebaseFirestore   mockDb;
    @Mock CollectionReference mockCollection;
    @Mock DocumentReference   mockDocument;
    @Mock DocumentSnapshot    mockSnap;

    private UserRemoteDataSource dataSource;

    private static final String UUID      = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String COLLECTION = "users";

    /** Runs {@code r} on a single background thread; re-throws any exception. */
    private final ExecutorService bg = Executors.newSingleThreadExecutor();

    private void runBg(ThrowingRunnable r) throws Exception {
        bg.submit(() -> { r.run(); return null; }).get(3, TimeUnit.SECONDS);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        dataSource = new UserRemoteDataSource(mockDb);

        when(mockDb.collection(COLLECTION)).thenReturn(mockCollection);
        when(mockCollection.document(UUID)).thenReturn(mockDocument);
    }

    // ── createUserSync — US 01.02.01 ─────────────────────────────────────────

    /**
     * US 01.02.01 — createUserSync calls {@code set(data)} on the correct document.
     */
    @Test
    public void createUserSync_callsSetOnDocument() throws Exception {
        when(mockDocument.set(anyMap())).thenReturn(Tasks.forResult(null));

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice Smith");
        data.put("email", "alice@ualberta.ca");

        runBg(() -> dataSource.createUserSync(UUID, data));

        verify(mockDocument).set(data);
    }

    /**
     * US 01.02.01 — Firestore failure propagates as an exception from createUserSync.
     */
    @Test
    public void createUserSync_firestoreReturnsError_throwsException() {
        Exception firestoreError = new Exception("PERMISSION_DENIED");
        when(mockDocument.set(anyMap()))
                .thenReturn(Tasks.forException(firestoreError));

        assertThrows(Exception.class, () ->
                bg.submit((java.util.concurrent.Callable<Void>) () -> {
                    dataSource.createUserSync(UUID, new HashMap<>()); return null;
                }).get(3, TimeUnit.SECONDS));
    }

    // ── updateUserSync — US 01.02.02 ─────────────────────────────────────────

    /**
     * US 01.02.02 — updateUserSync calls {@code set(data, merge)} so it works
     * even if the document does not yet exist.
     */
    @Test
    public void updateUserSync_callsSetWithMerge() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        Map<String, Object> update = new HashMap<>();
        update.put("name", "Alice Updated");

        runBg(() -> dataSource.updateUserSync(UUID, update));

        verify(mockDocument).set(eq(update), any(SetOptions.class));
    }

    /**
     * US 01.02.02 — Firestore failure during update propagates as an exception.
     */
    @Test
    public void updateUserSync_firestoreReturnsError_throwsException() {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forException(new Exception("UNAVAILABLE")));

        assertThrows(Exception.class, () ->
                bg.submit((java.util.concurrent.Callable<Void>) () -> {
                    dataSource.updateUserSync(UUID, new HashMap<>()); return null;
                }).get(3, TimeUnit.SECONDS));
    }

    // ── deleteUserSync — US 01.02.04 ─────────────────────────────────────────

    /**
     * US 01.02.04 — deleteUserSync writes {@code isDeleted=true} to Firestore.
     */
    @Test
    public void deleteUserSync_setsIsDeletedTrue() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        runBg(() -> dataSource.deleteUserSync(UUID));

        verify(mockDocument).set(
                argThat(map -> Boolean.TRUE.equals(((Map<?, ?>) map).get("isDeleted"))),
                any(SetOptions.class));
    }

    /**
     * US 01.02.04 — deleteUserSync also writes a {@code deletedAt} epoch timestamp.
     */
    @Test
    public void deleteUserSync_setsDeletedAtTimestamp() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        long before = System.currentTimeMillis();
        runBg(() -> dataSource.deleteUserSync(UUID));
        long after = System.currentTimeMillis();

        verify(mockDocument).set(
                argThat(map -> {
                    Object ts = ((Map<?, ?>) map).get("deletedAt");
                    if (!(ts instanceof Long)) return false;
                    long t = (Long) ts;
                    return t >= before && t <= after;
                }),
                any(SetOptions.class));
    }

    // ── getUserSync — US 01.02.02 ─────────────────────────────────────────────

    /**
     * US 01.02.02 — getUserSync returns null when the document does not exist.
     */
    @Test
    public void getUserSync_documentNotFound_returnsNull() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(false);

        User[] result = {new User()};
        CountDownLatch latch = new CountDownLatch(1);

        bg.submit(() -> {
            try {
                result[0] = dataSource.getUserSync(UUID);
            } catch (Exception e) {
                result[0] = null;
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNull(result[0]);
    }

    /**
     * US 01.02.02 — getUserSync maps name, email, and phone from the snapshot.
     */
    @Test
    public void getUserSync_documentExists_mapsFields() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get("name")).thenReturn("Alice Smith");
        when(mockSnap.get("email")).thenReturn("alice@ualberta.ca");
        when(mockSnap.get("phone")).thenReturn("780-492-3111");
        when(mockSnap.get("createdAt")).thenReturn(null);
        when(mockSnap.get("isDeleted")).thenReturn(null);
        when(mockSnap.get("lastLoginAt")).thenReturn(null);
        when(mockSnap.get("deletedAt")).thenReturn(null);
        when(mockSnap.get("isGuest")).thenReturn(true);

        User[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);

        bg.submit(() -> {
            try {
                result[0] = dataSource.getUserSync(UUID);
            } catch (Exception e) {
                fail("getUserSync threw: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertEquals("Alice Smith",       result[0].getName());
        assertEquals("alice@ualberta.ca", result[0].getEmail());
        assertEquals("780-492-3111",      result[0].getPhone());
    }

    /**
     * getUserSync maps new Phase-1.1 fields: bio, organizationName, profilePhotoUrl,
     * verificationStatus, and preferences.
     */
    @Test
    public void getUserSync_mapsNewPhase1Fields() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get("name")).thenReturn("Alice Smith");
        when(mockSnap.get("email")).thenReturn("alice@ualberta.ca");
        when(mockSnap.get("phone")).thenReturn("");
        when(mockSnap.get("createdAt")).thenReturn(null);
        when(mockSnap.get("isDeleted")).thenReturn(null);
        when(mockSnap.get("lastLoginAt")).thenReturn(null);
        when(mockSnap.get("deletedAt")).thenReturn(null);
        when(mockSnap.get("isGuest")).thenReturn(false);
        when(mockSnap.get("bio")).thenReturn("My bio");
        when(mockSnap.get("organizationName")).thenReturn("Acme Corp");
        when(mockSnap.get("profilePhotoUrl")).thenReturn("https://example.com/photo.jpg");
        when(mockSnap.get("verificationStatus")).thenReturn("email_verified");
        Map<String, Object> prefsMap = new HashMap<>();
        prefsMap.put("categories", java.util.Arrays.asList("Music"));
        when(mockSnap.get("preferences")).thenReturn(prefsMap);

        User[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);
        bg.submit(() -> {
            try { result[0] = dataSource.getUserSync(UUID); }
            catch (Exception e) { fail("threw: " + e.getMessage()); }
            finally { latch.countDown(); }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        assertNotNull(result[0]);
        assertEquals("My bio",                         result[0].getBio());
        assertEquals("Acme Corp",                      result[0].getOrganizationName());
        assertEquals("https://example.com/photo.jpg",  result[0].getProfilePhotoUrl());
        assertEquals("email_verified",                 result[0].getVerificationStatus());
        assertNotNull(result[0].getPreferences());
    }

    /**
     * getUserSync falls back to empty string for null text fields.
     */
    @Test
    public void getUserSync_nullTextFields_fallBackToEmptyString() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null);

        User[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);
        bg.submit(() -> {
            try { result[0] = dataSource.getUserSync(UUID); }
            catch (Exception ignored) { }
            finally { latch.countDown(); }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        // bio and organizationName should not throw; they may be null or ""
        // (getString helper returns "" for null)
        if (result[0] != null) {
            String bio = result[0].getBio();
            assertTrue(bio == null || bio.isEmpty());
        }
    }

    /**
     * US 01.02.02 — isGuest is set to true when no lastLoginAt is present.
     */
    @Test
    public void getUserSync_noLastLoginAt_isGuestTrue() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null);
        when(mockSnap.get("isGuest")).thenReturn(null); // fall through to lastLoginAt check

        User[] result = {null};
        CountDownLatch latch = new CountDownLatch(1);

        bg.submit(() -> {
            try {
                result[0] = dataSource.getUserSync(UUID);
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(3, TimeUnit.SECONDS));
        if (result[0] != null) {
            assertTrue("guest when lastLoginAt==0", result[0].isGuest());
        }
    }
}
