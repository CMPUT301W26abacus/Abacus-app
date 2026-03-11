package com.example.abacus_app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for the User model class.
 *
 * Covers:
 *   US 01.07.01 — Device-based identification (uid field)
 *   US 01.02.01 — Create profile: name, email, phone
 *   US 01.02.02 — Update profile: setters update fields
 *   US 01.02.04 — Delete profile: isDeleted / deletedAt flags
 *
 * No Android APIs used — runs on JVM directly.
 */
@RunWith(JUnit4.class)
public class UserTest {

    // ── Constructor ──────────────────────────────────────────────────────────

    /** US 01.07.01 — uid is stored and returned correctly. */
    @Test
    public void constructor_setsUid() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertEquals("uid-abc", user.getUid());
    }

    /** US 01.02.01 — name is stored and returned correctly. */
    @Test
    public void constructor_setsName() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertEquals("Alice Smith", user.getName());
    }

    /** US 01.02.01 — email is stored and returned correctly. */
    @Test
    public void constructor_setsEmail() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertEquals("alice@ualberta.ca", user.getEmail());
    }

    /** US 01.02.01 — phone defaults to empty string (not null) on construction. */
    @Test
    public void constructor_phoneDefaultsToEmptyString() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertNotNull(user.getPhone());
        assertEquals("", user.getPhone());
    }

    /** US 01.07.01 — new users are guests by default (no login yet). */
    @Test
    public void constructor_isGuestByDefault() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertTrue(user.isGuest());
    }

    /** US 01.02.04 — isDeleted is false when a user is first created. */
    @Test
    public void constructor_isNotDeletedByDefault() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertFalse(user.isDeleted());
    }

    /** deletedAt epoch is 0 when a user is first created. */
    @Test
    public void constructor_deletedAtIsZeroByDefault() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertEquals(0L, user.getDeletedAt());
    }

    /** Default role is "entrant" for a newly registered user. */
    @Test
    public void constructor_defaultRoleIsEntrant() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertEquals("entrant", user.getRole());
    }

    /** Notifications are enabled by default for new users. */
    @Test
    public void constructor_notificationsEnabledByDefault() {
        User user = new User("uid-abc", "alice@ualberta.ca", "Alice Smith", "2026-01-15");
        assertTrue(user.getNotificationsEnabled());
    }

    /** Firestore requires a no-arg constructor — it must not throw. */
    @Test
    public void defaultConstructor_doesNotThrow() {
        User user = new User();
        assertNotNull(user);
    }

    // ── Setters — US 01.02.02 (Update Profile) ───────────────────────────────

    /** US 01.02.02 — setName stores the updated name. */
    @Test
    public void setName_updatesNameField() {
        User user = new User();
        user.setName("Bob Johnson");
        assertEquals("Bob Johnson", user.getName());
    }

    /** US 01.02.02 — setEmail stores the updated email. */
    @Test
    public void setEmail_updatesEmailField() {
        User user = new User();
        user.setEmail("bob.johnson@ualberta.ca");
        assertEquals("bob.johnson@ualberta.ca", user.getEmail());
    }

    /** US 01.02.02 — setPhone stores the updated phone number. */
    @Test
    public void setPhone_updatesPhoneField() {
        User user = new User();
        user.setPhone("780-492-3111");
        assertEquals("780-492-3111", user.getPhone());
    }

    /** US 01.02.02 — name can be updated multiple times (last write wins). */
    @Test
    public void setName_canBeUpdatedRepeatedly() {
        User user = new User("uid", "e@e.com", "Alice", "2026-01-01");
        user.setName("Alice Updated");
        user.setName("Alice Final");
        assertEquals("Alice Final", user.getName());
    }

    // ── Delete — US 01.02.04 ────────────────────────────────────────────────

    /** US 01.02.04 — setDeleted(true) marks the profile as deleted. */
    @Test
    public void setDeleted_true_marksProfileAsDeleted() {
        User user = new User("uid-del", "carol@ualberta.ca", "Carol White", "2026-02-01");
        user.setDeleted(true);
        assertTrue(user.isDeleted());
    }

    /** US 01.02.04 — setDeletedAt records the deletion timestamp (epoch ms). */
    @Test
    public void setDeletedAt_storesDeletionTimestamp() {
        User user = new User();
        long now = 1741708800000L; // 2026-03-11 00:00:00 UTC
        user.setDeletedAt(now);
        assertEquals(now, user.getDeletedAt());
    }

    /** US 01.02.04 — a deleted user retains name/email until explicitly cleared. */
    @Test
    public void deleteProfile_doesNotAutoWipeNameOrEmail() {
        User user = new User("uid-del", "carol@ualberta.ca", "Carol White", "2026-02-01");
        user.setDeleted(true);
        user.setDeletedAt(System.currentTimeMillis());
        // Name and email are cleared by the ViewModel, not the model itself
        assertEquals("Carol White", user.getName());
        assertEquals("carol@ualberta.ca", user.getEmail());
    }

    // ── Role & notifications ─────────────────────────────────────────────────

    /** setRole stores a new role value. */
    @Test
    public void setRole_updatesRole() {
        User user = new User();
        user.setRole("organizer");
        assertEquals("organizer", user.getRole());
    }

    /** setNotificationsEnabled(false) disables push notifications for the user. */
    @Test
    public void setNotificationsEnabled_false_disablesNotifications() {
        User user = new User("uid", "e@e.com", "Alice", "2026-01-01");
        user.setNotificationsEnabled(false);
        assertFalse(user.getNotificationsEnabled());
    }

    /** setIsGuest(false) marks a returning user as non-guest. */
    @Test
    public void setIsGuest_false_marksUserAsNonGuest() {
        User user = new User("uid", "e@e.com", "Alice", "2026-01-01");
        user.setIsGuest(false);
        assertFalse(user.isGuest());
    }
}
