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
 * Unit tests for UserLocalDataSource.
 *
 * Covers:
 *   US 01.07.01 — Device-based identification
 *                 UUID is persisted locally and survives app restarts.
 *
 * Mocks SharedPreferences so no Android runtime is needed.
 */
@RunWith(MockitoJUnitRunner.class)
public class UserLocalDataSourceTest {

    @Mock Context           mockContext;
    @Mock SharedPreferences mockPrefs;
    @Mock SharedPreferences.Editor mockEditor;

    private UserLocalDataSource dataSource;

    @Before
    public void setUp() {
        when(mockContext.getSharedPreferences(
                UserLocalDataSource.PREFS_NAME, Context.MODE_PRIVATE))
                .thenReturn(mockPrefs);
        when(mockPrefs.edit()).thenReturn(mockEditor);
        when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);

        dataSource = new UserLocalDataSource(mockContext);
    }

    // ── getUUIDSync ──────────────────────────────────────────────────────────

    /**
     * US 01.07.01 — Returns null when no UUID has ever been saved
     *               (first app launch, no prior install).
     */
    @Test
    public void getUUIDSync_firstLaunch_returnsNull() {
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(null);

        assertNull(dataSource.getUUIDSync());
    }

    /**
     * US 01.07.01 — Returns the previously saved UUID on subsequent launches.
     */
    @Test
    public void getUUIDSync_existingUUID_returnsStoredValue() {
        String stored = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(stored);

        assertEquals(stored, dataSource.getUUIDSync());
    }

    /**
     * getDeviceId() is an alias for getUUIDSync() — must return identical result.
     */
    @Test
    public void getDeviceId_aliasForGetUUIDSync_returnsSameValue() {
        String stored = "550e8400-e29b-41d4-a716-446655440000";
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(stored);

        assertEquals(dataSource.getUUIDSync(), dataSource.getDeviceId());
    }

    // ── saveUUIDSync ─────────────────────────────────────────────────────────

    /**
     * US 01.07.01 — saveUUIDSync writes the UUID to SharedPreferences with the
     *               correct key so it can be retrieved after an app restart.
     */
    @Test
    public void saveUUIDSync_writesUUIDWithCorrectKey() {
        String uuid = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";

        dataSource.saveUUIDSync(uuid);

        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, uuid);
        verify(mockEditor).apply();
    }

    /**
     * US 01.07.01 — saveDeviceId() is an alias: it must also write to SharedPreferences.
     */
    @Test
    public void saveDeviceId_aliasForSaveUUIDSync_writesToPrefs() {
        String uuid = "6ba7b811-9dad-11d1-80b4-00c04fd430c8";

        dataSource.saveDeviceId(uuid);

        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, uuid);
        verify(mockEditor).apply();
    }

    /**
     * US 01.07.01 — Round-trip: save a UUID, then retrieve it.
     *               Simulates what happens across app restarts.
     */
    @Test
    public void saveAndGet_roundTrip_returnsSameUUID() {
        String uuid = "unique-device-id-edmonton-user";
        when(mockPrefs.getString(UserLocalDataSource.KEY_UUID, null)).thenReturn(uuid);

        dataSource.saveUUIDSync(uuid);

        assertEquals(uuid, dataSource.getUUIDSync());
    }

    /**
     * US 01.07.01 — A second save overwrites the first (device re-registration scenario).
     */
    @Test
    public void saveUUIDSync_calledTwice_secondValueIsStored() {
        dataSource.saveUUIDSync("first-uuid");
        dataSource.saveUUIDSync("second-uuid");

        // Both writes should have been attempted
        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, "first-uuid");
        verify(mockEditor).putString(UserLocalDataSource.KEY_UUID, "second-uuid");
    }
}