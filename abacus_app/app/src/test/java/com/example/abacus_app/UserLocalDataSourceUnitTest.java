package com.example.abacus_app;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserLocalDataSource}.
 *
 * Covers:
 *   US 01.07.01 — Device-based identification: UUID persisted locally.
 *
 * Runs on the JVM (no device or emulator required).
 * {@link Context} and {@link SharedPreferences} are mocked with Mockito.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserLocalDataSourceUnitTest {

    @Mock Context            mockContext;
    @Mock SharedPreferences  mockPrefs;
    @Mock SharedPreferences.Editor mockEditor;

    private UserLocalDataSource dataSource;

    private static final String ALICE_UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String BOB_UUID   = "550e8400-e29b-41d4-a716-446655440000";

    @Before
    public void setUp() {
        when(mockContext.getSharedPreferences(
                UserLocalDataSource.PREFS_NAME, Context.MODE_PRIVATE))
                .thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
        when(mockEditor.remove(anyString())).thenReturn(mockEditor);

        dataSource = new UserLocalDataSource(mockContext);
    }

    // ── getUUIDSync / getDeviceId ────────────────────────────────────────────

    /**
     * US 01.07.01 — Returns null on a fresh install (no UUID stored yet).
     */
    @Test
    public void getUUIDSync_noUUIDStored_returnsNull() {
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(null);
        assertNull(dataSource.getUUIDSync());
    }

    /**
     * US 01.07.01 — Returns the stored UUID on subsequent launches.
     */
    @Test
    public void getUUIDSync_uuidStored_returnsStoredValue() {
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(ALICE_UUID);
        assertEquals(ALICE_UUID, dataSource.getUUIDSync());
    }

    /**
     * {@code getDeviceId()} is an alias for {@code getUUIDSync()} — must return
     * the same value.
     */
    @Test
    public void getDeviceId_aliasForGetUUIDSync_returnsSameValue() {
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(BOB_UUID);
        assertEquals(dataSource.getUUIDSync(), dataSource.getDeviceId());
    }

    // ── saveUUIDSync / saveDeviceId ──────────────────────────────────────────

    /**
     * US 01.07.01 — saveUUIDSync writes to SharedPreferences with the correct
     * key so the value survives an app restart.
     */
    @Test
    public void saveUUIDSync_writesWithCorrectKey() {
        dataSource.saveUUIDSync(ALICE_UUID);

        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, ALICE_UUID);
        verify(mockEditor).apply();
    }

    /**
     * {@code saveDeviceId()} is an alias — it must also write to SharedPreferences.
     */
    @Test
    public void saveDeviceId_aliasForSaveUUIDSync_writesToPrefs() {
        dataSource.saveDeviceId(BOB_UUID);

        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, BOB_UUID);
        verify(mockEditor).apply();
    }

    /**
     * US 01.07.01 — Round-trip: save then retrieve returns the same UUID.
     */
    @Test
    public void saveAndGet_roundTrip_returnsSameUUID() {
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(ALICE_UUID);

        dataSource.saveUUIDSync(ALICE_UUID);

        assertEquals(ALICE_UUID, dataSource.getUUIDSync());
    }

    /**
     * Second save overwrites the first — the last write wins.
     */
    @Test
    public void saveUUIDSync_calledTwice_bothWritesAttempted() {
        dataSource.saveUUIDSync("first-uuid");
        dataSource.saveUUIDSync("second-uuid");

        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, "first-uuid");
        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, "second-uuid");
    }

    // ── clearDeviceId ────────────────────────────────────────────────────────

    /**
     * clearDeviceId removes the UUID key from SharedPreferences so the next
     * launch treats the device as a fresh install.
     */
    @Test
    public void clearDeviceId_removesUUIDKey() {
        dataSource.clearDeviceId();

        verify(mockEditor).remove(UserLocalDataSource.KEY_UUID);
        verify(mockEditor).apply();
    }

    /**
     * After clearing, a subsequent read returns null.
     */
    @Test
    public void clearDeviceId_thenGetUUIDSync_returnsNull() {
        dataSource.clearDeviceId();
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(null);

        assertNull(dataSource.getUUIDSync());
    }
}
