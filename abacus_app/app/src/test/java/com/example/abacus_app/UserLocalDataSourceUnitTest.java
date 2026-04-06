package com.example.abacus_app;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link UserLocalDataSource}.
 *
 * <p>Covers:
 *   US 01.07.01 — Device-based identification: UUID persisted locally in
 *   {@link androidx.security.crypto.EncryptedSharedPreferences}.
 *
 * <p>Runs on the JVM via Robolectric (required since {@link
 * androidx.security.crypto.EncryptedSharedPreferences} needs a real Android
 * {@link Context} to access the Keystore through Robolectric's shadow).
 *
 * <p>Robolectric's shadow for the Android Keystore initialises successfully,
 * so {@code EncryptedSharedPreferences.create()} does not fall back to plain
 * SharedPreferences in this environment.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, manifest = Config.NONE)
public class UserLocalDataSourceUnitTest {

    private static final String ALICE_UUID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private static final String BOB_UUID   = "550e8400-e29b-41d4-a716-446655440000";

    private UserLocalDataSource dataSource;

    @Before
    public void setUp() {
        Context ctx = RuntimeEnvironment.getApplication();
        dataSource = new UserLocalDataSource(ctx);
        // Always start from a clean slate so tests are independent
        dataSource.clearDeviceId();
    }

    // ── getUUIDSync / getDeviceId ────────────────────────────────────────────

    /**
     * US 01.07.01 — Returns null on a fresh install (no UUID stored yet).
     */
    @Test
    public void getUUIDSync_noUUIDStored_returnsNull() {
        assertNull(dataSource.getUUIDSync());
    }

    /**
     * US 01.07.01 — Returns the stored UUID on subsequent launches.
     */
    @Test
    public void getUUIDSync_uuidStored_returnsStoredValue() {
        dataSource.saveUUIDSync(ALICE_UUID);
        assertEquals(ALICE_UUID, dataSource.getUUIDSync());
    }

    /**
     * {@code getDeviceId()} is an alias for {@code getUUIDSync()} — must return
     * the same value.
     */
    @Test
    public void getDeviceId_aliasForGetUUIDSync_returnsSameValue() {
        dataSource.saveUUIDSync(BOB_UUID);
        assertEquals(dataSource.getUUIDSync(), dataSource.getDeviceId());
    }

    // ── saveUUIDSync / saveDeviceId ──────────────────────────────────────────

    /**
     * US 01.07.01 — Round-trip: save then retrieve returns the same UUID.
     */
    @Test
    public void saveAndGet_roundTrip_returnsSameUUID() {
        dataSource.saveUUIDSync(ALICE_UUID);
        assertEquals(ALICE_UUID, dataSource.getUUIDSync());
    }

    /**
     * {@code saveDeviceId()} is an alias — persists the same value.
     */
    @Test
    public void saveDeviceId_aliasForSaveUUIDSync_persistsValue() {
        dataSource.saveDeviceId(BOB_UUID);
        assertEquals(BOB_UUID, dataSource.getDeviceId());
    }

    /**
     * Second save overwrites the first — the last write wins.
     */
    @Test
    public void saveUUIDSync_calledTwice_lastValueWins() {
        dataSource.saveUUIDSync("first-uuid");
        dataSource.saveUUIDSync("second-uuid");
        assertEquals("second-uuid", dataSource.getUUIDSync());
    }

    // ── clearDeviceId ────────────────────────────────────────────────────────

    /**
     * After clearing, a subsequent read returns null.
     */
    @Test
    public void clearDeviceId_thenGetUUIDSync_returnsNull() {
        dataSource.saveUUIDSync(ALICE_UUID);
        dataSource.clearDeviceId();
        assertNull(dataSource.getUUIDSync());
    }

    /**
     * Calling clear when no UUID was stored must not throw.
     */
    @Test
    public void clearDeviceId_whenNothingStored_doesNotThrow() {
        dataSource.clearDeviceId(); // should be a no-op
        assertNull(dataSource.getUUIDSync());
    }

    // ── Encryption verification ──────────────────────────────────────────────

    /**
     * Data stored in EncryptedSharedPreferences must survive an instance
     * re-creation (simulates app restart).
     *
     * <p>A new {@link UserLocalDataSource} constructed with the same context
     * and prefs file name must be able to read the value written by the first
     * instance, confirming the encrypted file is shared correctly.
     */
    @Test
    public void encryptedPrefs_survivesInstanceRecreation() {
        dataSource.saveUUIDSync(ALICE_UUID);

        // Create a second instance pointing at the same encrypted prefs
        UserLocalDataSource secondInstance =
                new UserLocalDataSource(RuntimeEnvironment.getApplication());

        assertEquals(
                "UUID must be readable by a new instance (simulates app restart)",
                ALICE_UUID, secondInstance.getUUIDSync());
    }
}
