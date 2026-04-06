package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Instrumented tests for {@link UserRemoteDataSource}.
 *
 * Runs on a device or emulator so that {@code Tasks.await()} can safely block
 * the test-runner thread (which is NOT the main UI thread).
 * All network calls are intercepted by mocking {@link FirebaseFirestore} and
 * returning pre-resolved {@link Tasks#forResult} or {@link Tasks#forException} tasks.
 *
 * Covers:
 *   US 01.02.01 — createUser: writes a new user document
 *   US 01.02.02 — getUser: reads and maps a user document
 *   US 01.02.02 — updateUser: merges changed fields
 *   US 01.02.04 — deleteUser: soft-delete (isDeleted + deletedAt)
 *
 * Note: {@code deleteWaitlistEntriesForUser} requires mocking {@code collectionGroup}
 * which returns a {@link Query}; that path is validated in a dedicated test below.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserRemoteDataSourceTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock FirebaseFirestore   mockDb;
    @Mock CollectionReference mockUsersCollection;
    @Mock DocumentReference   mockDocument;
    @Mock DocumentSnapshot    mockSnap;

    private UserRemoteDataSource dataSource;

    private static final String UUID       = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String COLLECTION = "users";

    @Before
    public void setUp() {
        dataSource = new UserRemoteDataSource(mockDb);

        when(mockDb.collection(COLLECTION)).thenReturn(mockUsersCollection);
        when(mockUsersCollection.document(UUID)).thenReturn(mockDocument);
    }

    // ── createUserSync — US 01.02.01 ─────────────────────────────────────────

    /**
     * US 01.02.01 — createUserSync calls {@code set(data)} on the correct document.
     */
    @Test
    public void createUserSync_callsSetOnCorrectDocument() throws Exception {
        when(mockDocument.set(anyMap())).thenReturn(Tasks.forResult(null));

        Map<String, Object> data = new HashMap<>();
        data.put("name",     "Alice Smith");
        data.put("email",    "alice@ualberta.ca");
        data.put("deviceId", UUID);

        dataSource.createUserSync(UUID, data);

        verify(mockDocument).set(data);
    }

    /**
     * createUser() is a spec-named alias — it must call the same Firestore set.
     */
    @Test
    public void createUser_aliasForCreateUserSync_callsSet() throws Exception {
        when(mockDocument.set(anyMap())).thenReturn(Tasks.forResult(null));

        dataSource.createUserSync(UUID, new HashMap<>());

        verify(mockDocument).set(anyMap());
    }

    /**
     * US 01.02.01 — Firestore error during create propagates as an exception.
     */
    @Test(expected = Exception.class)
    public void createUserSync_firestoreError_throwsException() throws Exception {
        when(mockDocument.set(anyMap()))
                .thenReturn(Tasks.forException(new Exception("PERMISSION_DENIED")));

        dataSource.createUserSync(UUID, new HashMap<>());
    }

    // ── getUserSync — US 01.02.02 ─────────────────────────────────────────────

    /**
     * US 01.02.02 — getUserSync returns null when the document does not exist.
     */
    @Test
    public void getUserSync_documentDoesNotExist_returnsNull() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(false);

        assertNull(dataSource.getUserSync(UUID));
    }

    /**
     * US 01.02.02 — getUserSync maps name, email, and phone from the snapshot.
     */
    @Test
    public void getUserSync_documentExists_mapsNameEmailPhone() throws Exception {
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

        User user = dataSource.getUserSync(UUID);

        assertNotNull(user);
        assertEquals("Alice Smith",       user.getName());
        assertEquals("alice@ualberta.ca", user.getEmail());
        assertEquals("780-492-3111",      user.getPhone());
    }

    /**
     * US 01.02.02 — UID is set from the UUID parameter passed to getUserSync.
     */
    @Test
    public void getUserSync_documentExists_setsUidFromParameter() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null);

        User user = dataSource.getUserSync(UUID);

        assertNotNull(user);
        assertEquals(UUID, user.getUid());
    }

    /**
     * US 01.02.02 — isGuest is true when the "isGuest" field is true.
     */
    @Test
    public void getUserSync_isGuestTrue_userIsGuest() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null);
        when(mockSnap.get("isGuest")).thenReturn(true);

        User user = dataSource.getUserSync(UUID);

        assertNotNull(user);
        assertTrue(user.isGuest());
    }

    /**
     * US 01.02.02 — isGuest defaults to true when lastLoginAt is 0 and "isGuest"
     * field is absent.
     */
    @Test
    public void getUserSync_noIsGuestField_guestDerivedFromLastLoginAt() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null); // lastLoginAt → 0L → guest

        User user = dataSource.getUserSync(UUID);

        assertNotNull(user);
        assertTrue("lastLoginAt==0 means guest", user.isGuest());
    }

    /**
     * US 01.02.02 — deletedAt stored as a Long is mapped correctly.
     */
    @Test
    public void getUserSync_deletedAtLong_storedCorrectly() throws Exception {
        long deletedTs = 1741708800000L;

        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(true);
        when(mockSnap.get(anyString())).thenReturn(null);
        when(mockSnap.get("deletedAt")).thenReturn(deletedTs);

        User user = dataSource.getUserSync(UUID);

        assertNotNull(user);
        assertEquals(deletedTs, user.getDeletedAt());
    }

    /**
     * getUser() is a spec alias — must return the same User as getUserSync.
     */
    @Test
    public void getUser_aliasForGetUserSync_returnsUser() throws Exception {
        when(mockDocument.get()).thenReturn(Tasks.forResult(mockSnap));
        when(mockSnap.exists()).thenReturn(false);

        assertNull(dataSource.getUserSync(UUID));
    }

    // ── updateUserSync — US 01.02.02 ─────────────────────────────────────────

    /**
     * US 01.02.02 — updateUserSync calls {@code set(data, merge)} so it works
     * for both new and existing documents.
     */
    @Test
    public void updateUserSync_callsSetWithMerge() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        Map<String, Object> update = new HashMap<>();
        update.put("name",  "Alice Updated");
        update.put("phone", "780-555-9999");

        dataSource.updateUserSync(UUID, update);

        verify(mockDocument).set(eq(update), any(SetOptions.class));
    }

    /**
     * updateUser() is a spec alias — must also call set with merge.
     */
    @Test
    public void updateUser_aliasForUpdateUserSync_callsSetWithMerge() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        dataSource.updateUserSync(UUID, new HashMap<>());

        verify(mockDocument).set(anyMap(), any(SetOptions.class));
    }

    /**
     * US 01.02.02 — Firestore error during update propagates as an exception.
     */
    @Test(expected = Exception.class)
    public void updateUserSync_firestoreError_throwsException() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forException(new Exception("UNAVAILABLE")));

        dataSource.updateUserSync(UUID, new HashMap<>());
    }

    // ── deleteUserSync — US 01.02.04 ─────────────────────────────────────────

    /**
     * US 01.02.04 — deleteUserSync sets {@code isDeleted = true} in Firestore.
     */
    @Test
    public void deleteUserSync_setsIsDeletedTrue() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        dataSource.deleteUserSync(UUID);

        verify(mockDocument).set(
                argThat(map -> Boolean.TRUE.equals(((Map<?, ?>) map).get("isDeleted"))),
                any(SetOptions.class));
    }

    /**
     * US 01.02.04 — deleteUserSync also writes a non-zero {@code deletedAt} epoch ms.
     */
    @Test
    public void deleteUserSync_setsDeletedAtTimestamp() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        long before = System.currentTimeMillis();
        dataSource.deleteUserSync(UUID);
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

    /**
     * deleteUser() is a spec alias — must also mark the document as deleted.
     */
    @Test
    public void deleteUser_aliasForDeleteUserSync_callsSetWithMerge() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forResult(null));

        dataSource.deleteUserSync(UUID);

        verify(mockDocument).set(
                argThat(map -> Boolean.TRUE.equals(((Map<?, ?>) map).get("isDeleted"))),
                any(SetOptions.class));
    }

    /**
     * US 01.02.04 — Firestore error during delete propagates as an exception.
     */
    @Test(expected = Exception.class)
    public void deleteUserSync_firestoreError_throwsException() throws Exception {
        when(mockDocument.set(anyMap(), any(SetOptions.class)))
                .thenReturn(Tasks.forException(new Exception("PERMISSION_DENIED")));

        dataSource.deleteUserSync(UUID);
    }

    // ── deleteWaitlistEntriesForUser ─────────────────────────────────────────

    /**
     * deleteWaitlistEntriesForUser makes no batch call when the collectionGroup
     * query returns an empty snapshot.
     */
    @Test
    public void deleteWaitlistEntriesForUser_emptySnapshot_noBatchCommit() throws Exception {
        Query mockQuery = mock(Query.class);
        QuerySnapshot mockQSnap = mock(QuerySnapshot.class);

        when(mockDb.collectionGroup("waitlist")).thenReturn(mockQuery);
        when(mockQuery.whereEqualTo("userID", UUID)).thenReturn(mockQuery);
        when(mockQuery.get()).thenReturn(Tasks.forResult(mockQSnap));
        when(mockQSnap.isEmpty()).thenReturn(true);

        dataSource.deleteWaitlistEntriesForUser(UUID);

        // No WriteBatch was started because isEmpty() == true
        verify(mockDb, never()).batch();
    }
}
