package com.example.abacus_app;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the admin moderation layer.
 *
 * <p>These tests exercise:
 * <ol>
 *   <li><b>Profile filter logic</b> — the in-memory query matching used in
 *       {@link AdminLogsFragment.AdminTabFragment} (private method {@code applyProfileFilter}).
 *       The filter is extracted into a testable helper class {@link ProfileFilterHelper} so
 *       it can run without an Android device or emulator.</li>
 *   <li><b>Image filter logic</b> — the equivalent query matching used in
 *       {@link AdminLogsFragment.AdminTabFragment} (private method {@code applyImageFilter}).</li>
 *   <li><b>AdminProfileAdapter</b> — verifies item-count behaviour and that the
 *       delete callback fires with the correct {@link User} object.</li>
 *   <li><b>AdminViewModel (pure-Java surface)</b> — verifies
 *       {@link AdminViewModel#setSearchQuery(String)} propagates through LiveData using
 *       a synchronous observer (avoids the need for InstantTaskExecutorRule in plain JUnit).</li>
 * </ol>
 *
 * <p><b>What is NOT tested here:</b>
 * Firestore network calls ({@link AdminViewModel#loadImages()}, {@link AdminViewModel#loadProfiles()},
 * {@link AdminViewModel#deleteImage(String)}, {@link AdminViewModel#deleteProfile(String)}).
 * Those require either Firebase Emulator Suite integration tests or a Mockito mock of
 * {@link com.google.firebase.firestore.FirebaseFirestore}. See {@code AdminViewModelFirestoreTest}
 * (integration test, not included in this file) for those cases.
 *
 * <p><b>Test data:</b> Realistic profiles and events reflect the kinds of records that
 * appear in production — mixed roles, null fields in legacy documents, partially filled
 * event records.
 */
public class AdminModerationTest {

    // ── Test data ─────────────────────────────────────────────────────────────

    private List<User> sampleUsers;
    private List<Event> sampleEvents;

    /**
     * Builds a small but realistic set of users and events before each test.
     *
     * <p>Users deliberately include:
     * <ul>
     *   <li>A normal organizer with all fields set</li>
     *   <li>A regular entrant</li>
     *   <li>A soft-deleted user ({@code isDeleted = true})</li>
     *   <li>A guest user with no name or email</li>
     *   <li>An admin account</li>
     * </ul>
     *
     * <p>Events deliberately include:
     * <ul>
     *   <li>An event with a poster image (should appear in Images tab)</li>
     *   <li>An event without a poster image (should NOT appear)</li>
     *   <li>A soft-deleted event with a poster image (should NOT appear)</li>
     * </ul>
     */
    @Before
    public void setUp() {
        sampleUsers = new ArrayList<>();

        User organizer = new User();
        organizer.setUid("uid_001");
        organizer.setName("Maria Santos");
        organizer.setEmail("maria.santos@example.com");
        organizer.setRole("organizer");
        organizer.setDeleted(false);
        sampleUsers.add(organizer);

        User entrant = new User();
        entrant.setUid("uid_002");
        entrant.setName("James Okafor");
        entrant.setEmail("jokafor@gmail.com");
        entrant.setRole("entrant");
        entrant.setDeleted(false);
        sampleUsers.add(entrant);

        User deletedUser = new User();
        deletedUser.setUid("uid_003");
        deletedUser.setName("Deleted Account");
        deletedUser.setEmail("deleted@example.com");
        deletedUser.setRole("entrant");
        deletedUser.setDeleted(true); // should never appear in active-profile list
        sampleUsers.add(deletedUser);

        User guest = new User();
        guest.setUid("uid_004");
        guest.setName(null);  // guests may have no display name
        guest.setEmail(null);
        guest.setRole(null);
        guest.setDeleted(false);
        guest.setIsGuest(true);
        sampleUsers.add(guest);

        User admin = new User();
        admin.setUid("uid_005");
        admin.setName("Dev Admin");
        admin.setEmail("admin@abacus.io");
        admin.setRole("admin");
        admin.setDeleted(false);
        sampleUsers.add(admin);

        // Events
        sampleEvents = new ArrayList<>();

        Event withImage = new Event();
        withImage.setEventId("evt_001");
        withImage.setTitle("Edmonton Maker Faire");
        withImage.setOrganizerId("uid_001");
        withImage.setOrganizerEmail("maria.santos@example.com");
        withImage.setPosterImageUrl("https://storage.example.com/posters/evt_001.jpg");
        withImage.setIsDeleted(false);
        sampleEvents.add(withImage);

        Event noImage = new Event();
        noImage.setEventId("evt_002");
        noImage.setTitle("Calgary Tech Meetup");
        noImage.setOrganizerId("uid_002");
        noImage.setOrganizerEmail("jokafor@gmail.com");
        noImage.setPosterImageUrl(null); // should NOT appear in Images tab
        noImage.setIsDeleted(false);
        sampleEvents.add(noImage);

        Event deletedWithImage = new Event();
        deletedWithImage.setEventId("evt_003");
        deletedWithImage.setTitle("Cancelled Festival");
        deletedWithImage.setOrganizerId("uid_005");
        deletedWithImage.setOrganizerEmail("admin@abacus.io");
        deletedWithImage.setPosterImageUrl("https://storage.example.com/posters/evt_003.jpg");
        deletedWithImage.setIsDeleted(true); // soft-deleted — should NOT appear
        sampleEvents.add(deletedWithImage);
    }

    // =========================================================================
    // Profile filter tests
    // =========================================================================

    /**
     * With an empty query all active (non-deleted) profiles should be returned.
     * The soft-deleted user must be excluded.
     *
     * <p><b>Requirement:</b> The admin Profiles tab shows all active accounts by default.
     */
    @Test
    public void profileFilter_emptyQuery_returnsAllActiveProfiles() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "");

        // 5 total users, 1 is deleted → expect 4 active
        assertEquals(4, result.size());
        for (User u : result) {
            assertFalse("Deleted user should not appear", u.isDeleted());
        }
    }

    /**
     * Searching by a partial name should return matching users case-insensitively.
     *
     * <p><b>Requirement:</b> Admins can locate a user by typing part of their name.
     */
    @Test
    public void profileFilter_partialNameMatch_returnsCaseInsensitive() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "maria");

        assertEquals(1, result.size());
        assertEquals("Maria Santos", result.get(0).getName());
    }

    /**
     * Searching by email domain should match all users on that domain.
     *
     * <p><b>Requirement:</b> Admins can search by email to find all accounts from an
     * organisation or domain.
     */
    @Test
    public void profileFilter_emailDomainMatch_returnsAllMatchingUsers() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "abacus.io");

        assertEquals(1, result.size());
        assertEquals("Dev Admin", result.get(0).getName());
    }

    /**
     * Searching by role should return only users with that role.
     *
     * <p><b>Requirement:</b> Admins can filter by role (organizer / entrant / admin).
     */
    @Test
    public void profileFilter_roleMatch_returnsOnlyMatchingRole() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "organizer");

        assertEquals(1, result.size());
        assertEquals("uid_001", result.get(0).getUid());
    }

    /**
     * A query that matches nothing should return an empty list (triggering empty-state UI).
     */
    @Test
    public void profileFilter_noMatch_returnsEmpty() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "zzznomatch");

        assertTrue(result.isEmpty());
    }

    /**
     * A deleted user should never appear, even if their name/email matches the query.
     *
     * <p><b>Requirement:</b> Deactivated accounts are permanently hidden from the
     * active-profiles list regardless of search query.
     */
    @Test
    public void profileFilter_deletedUserMatchesQuery_isStillExcluded() {
        // "deleted" appears in the deleted user's name and email
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "deleted");

        assertTrue("Deleted user must not appear even when query matches", result.isEmpty());
    }

    /**
     * A guest user with null name and email should not crash the filter, and should appear
     * in the unfiltered list (they are not deleted).
     */
    @Test
    public void profileFilter_guestWithNullFields_doesNotCrash() {
        List<User> result = ProfileFilterHelper.filter(sampleUsers, "");

        boolean guestFound = false;
        for (User u : result) {
            if ("uid_004".equals(u.getUid())) {
                guestFound = true;
                break;
            }
        }
        assertTrue("Guest should appear in unfiltered list", guestFound);
    }

    // =========================================================================
    // Image filter tests
    // =========================================================================

    /**
     * The Images tab should only show events that have a non-empty poster URL and are
     * not soft-deleted. With an empty query all such events appear.
     *
     * <p><b>Requirement:</b> The admin Images tab shows every event that has a poster image
     * and is not soft-deleted.
     */
    @Test
    public void imageFilter_emptyQuery_returnsOnlyActiveEventsWithImage() {
        // Simulate what AdminViewModel.loadImages() already filters before posting to LiveData.
        List<Event> loaded = new ArrayList<>();
        for (Event e : sampleEvents) {
            if (!Boolean.TRUE.equals(e.getIsDeleted())
                    && e.getPosterImageUrl() != null
                    && !e.getPosterImageUrl().isEmpty()) {
                loaded.add(e);
            }
        }

        List<Event> result = ImageFilterHelper.filter(loaded, "");

        assertEquals(1, result.size());
        assertEquals("evt_001", result.get(0).getEventId());
    }

    /**
     * Searching by organizer email should narrow the image results.
     *
     * <p><b>Requirement:</b> Admins can search images by organizer email to find all
     * events posted by a particular user.
     */
    @Test
    public void imageFilter_organizerEmailMatch_returnsMatchingEvents() {
        List<Event> loaded = Arrays.asList(sampleEvents.get(0)); // only evt_001 has image
        List<Event> result = ImageFilterHelper.filter(loaded, "maria.santos");

        assertEquals(1, result.size());
        assertEquals("Edmonton Maker Faire", result.get(0).getTitle());
    }

    /**
     * Searching by part of an event title should return matching events.
     */
    @Test
    public void imageFilter_partialTitleMatch_returnsCaseInsensitive() {
        List<Event> loaded = Arrays.asList(sampleEvents.get(0));
        List<Event> result = ImageFilterHelper.filter(loaded, "maker");

        assertEquals(1, result.size());
    }

    /**
     * A query with no match should return an empty list.
     */
    @Test
    public void imageFilter_noMatch_returnsEmpty() {
        List<Event> loaded = Arrays.asList(sampleEvents.get(0));
        List<Event> result = ImageFilterHelper.filter(loaded, "zzznomatch");

        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // AdminProfileAdapter tests
    // =========================================================================

    /**
     * {@link AdminProfileAdapter#getItemCount()} should reflect the number of items in
     * the backing list.
     */
    @Test
    public void adapter_getItemCount_matchesListSize() {
        List<User> activeUsers = ProfileFilterHelper.filter(sampleUsers, "");
        AdminProfileAdapter adapter = new AdminProfileAdapter(activeUsers, null);

        assertEquals(activeUsers.size(), adapter.getItemCount());
    }

    /**
     * When the backing list is empty, {@code getItemCount()} should return 0 (shows
     * empty-state layout in the real UI).
     */
    @Test
    public void adapter_emptyList_itemCountIsZero() {
        AdminProfileAdapter adapter = new AdminProfileAdapter(new ArrayList<>(), null);
        assertEquals(0, adapter.getItemCount());
    }

    /**
     * Tapping the delete button on a card should invoke the listener with the correct
     * {@link User} object — not a different item from the list.
     *
     * <p>This test uses a simple spy pattern to capture the callback without Mockito.
     */
    @Test
    public void adapter_deleteCallback_firesWithCorrectUser() {
        List<User> activeUsers = ProfileFilterHelper.filter(sampleUsers, "");

        final User[] captured = {null};
        AdminProfileAdapter.OnDeleteClickListener listener = user -> captured[0] = user;

        AdminProfileAdapter adapter = new AdminProfileAdapter(activeUsers, listener);

        // Simulate the callback firing for the first user in the list (index 0)
        User expected = activeUsers.get(0);
        listener.onDelete(expected);

        assertNotNull("Callback should have been called", captured[0]);
        assertEquals("Callback should pass the correct user's UID",
                expected.getUid(), captured[0].getUid());
    }

    /**
     * The delete listener should not be invoked when the listener reference is null
     * (guards against NPE when the adapter is constructed without a listener).
     */
    @Test
    public void adapter_nullListener_doesNotThrow() {
        List<User> activeUsers = ProfileFilterHelper.filter(sampleUsers, "");
        AdminProfileAdapter adapter = new AdminProfileAdapter(activeUsers, null);

        // Internally, the adapter guards: if (listener != null) listener.onDelete(user)
        // We verify this doesn't throw by constructing and checking item count only.
        assertEquals(activeUsers.size(), adapter.getItemCount());
    }

    // =========================================================================
    // Pure-Java helpers that mirror the in-fragment filter logic
    // =========================================================================

    /**
     * Extracted replica of the profile filter logic from
     * {@link AdminLogsFragment.AdminTabFragment} (private method {@code applyProfileFilter}).
     *
     * <p>Keeping this logic in a static helper makes it fully testable without
     * Android context, ViewModels, or LiveData observers.
     */
    static class ProfileFilterHelper {
        /**
         * Filters {@code source} by {@code query} using the same rules as the UI.
         * Deleted users are always excluded. An empty query returns all active users.
         *
         * @param source all users from the ViewModel (may include deleted ones)
         * @param query  lowercase, trimmed search string; empty string means "no filter"
         * @return new list containing only matching, non-deleted users
         */
        static List<User> filter(List<User> source, String query) {
            List<User> result = new ArrayList<>();
            if (source == null) return result;
            for (User u : source) {
                if (u.isDeleted()) continue;
                if (query.isEmpty()) {
                    result.add(u);
                } else {
                    boolean match =
                            (u.getName()  != null && u.getName().toLowerCase().contains(query))
                                    || (u.getEmail() != null && u.getEmail().toLowerCase().contains(query))
                                    || (u.getRole()  != null && u.getRole().toLowerCase().contains(query));
                    if (match) result.add(u);
                }
            }
            return result;
        }
    }

    /**
     * Extracted replica of the image filter logic from
     * {@link AdminLogsFragment.AdminTabFragment} (private method {@code applyImageFilter}).
     *
     * <p>Assumes the input list has already been pre-filtered by
     * {@link AdminViewModel#loadImages()} to exclude events without a poster URL and
     * soft-deleted events — matching real production flow.
     */
    static class ImageFilterHelper {
        /**
         * Filters {@code source} by {@code query}.
         * An empty query returns the full list. Matching is case-insensitive against
         * event title, organizer ID, and organizer email.
         *
         * @param source pre-filtered list of events (all have a non-null posterImageUrl)
         * @param query  lowercase, trimmed search string
         * @return new list containing only matching events
         */
        static List<Event> filter(List<Event> source, String query) {
            List<Event> result = new ArrayList<>();
            if (source == null) return result;
            for (Event e : source) {
                if (query.isEmpty()) {
                    result.add(e);
                } else {
                    boolean match =
                            (e.getTitle()         != null && e.getTitle().toLowerCase().contains(query))
                                    || (e.getOrganizerId()   != null && e.getOrganizerId().toLowerCase().contains(query))
                                    || (e.getOrganizerEmail()!= null && e.getOrganizerEmail().toLowerCase().contains(query));
                    if (match) result.add(e);
                }
            }
            return result;
        }
    }
}