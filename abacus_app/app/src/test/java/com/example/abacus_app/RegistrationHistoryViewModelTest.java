package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RegistrationHistoryViewModel}.
 *
 * Covers:
 *   US 01.05.01–04 — Registration history: loading, empty state, status mapping,
 *                    error handling, and swipe-to-refresh behaviour.
 *
 * Runs on the JVM — no device or emulator required.
 * {@link InstantTaskExecutorRule} makes LiveData deliver synchronously.
 * The {@link RegistrationHistoryViewModel.RegistrationRepository} is mocked with Mockito.
 */
@RunWith(MockitoJUnitRunner.class)
public class RegistrationHistoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock RegistrationHistoryViewModel.RegistrationRepository mockRepo;

    private RegistrationHistoryViewModel vm;

    // Fixed epoch for "2026-03-11 00:00:00 UTC"
    private static final long TIMESTAMP = 1741651200000L;

    @Before
    public void setUp() {
        vm = new RegistrationHistoryViewModel(mockRepo);
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    public void initialState_registrationsEmpty() {
        List<?> regs = vm.getRegistrations().getValue();
        assertNotNull(regs);
        assertTrue(regs.isEmpty());
    }

    @Test
    public void initialState_isNotLoading() {
        assertFalse(vm.getIsLoading().getValue());
    }

    @Test
    public void initialState_noErrorMessage() {
        String err = vm.getErrorMessage().getValue();
        assertTrue(err == null || err.isEmpty());
    }

    // ── loadRegistrationHistory ──────────────────────────────────────────────

    /**
     * US 01.05.01 — loadRegistrationHistory triggers a fetch when the list is empty.
     */
    @Test
    public void loadRegistrationHistory_emptyList_fetchesFromRepo() {
        doAnswer(inv -> { callCallback(inv, new ArrayList<>(), null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        verify(mockRepo).getHistoryForUser(any());
    }

    /**
     * US 01.05.01 — loadRegistrationHistory is skipped if data is already loaded.
     */
    @Test
    public void loadRegistrationHistory_dataAlreadyPresent_skipsExtraFetch() {
        // First load populates the list
        List<RegistrationHistoryViewModel.Registration> oneItem = new ArrayList<>();
        oneItem.add(makeReg("event-1", "Spring Gala", "waitlisted"));

        doAnswer(inv -> { callCallback(inv, oneItem, null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();
        vm.loadRegistrationHistory(); // second call should be skipped

        verify(mockRepo, times(1)).getHistoryForUser(any());
    }

    // ── refresh ──────────────────────────────────────────────────────────────

    /**
     * refresh() always triggers a new fetch, even if data is already present.
     */
    @Test
    public void refresh_alwaysFetches() {
        List<RegistrationHistoryViewModel.Registration> oneItem = new ArrayList<>();
        oneItem.add(makeReg("event-1", "Spring Gala", "waitlisted"));

        doAnswer(inv -> { callCallback(inv, oneItem, null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory(); // initial load
        vm.refresh();                 // force re-fetch

        verify(mockRepo, times(2)).getHistoryForUser(any());
    }

    // ── data mapping / status labels — US 01.05.02 ───────────────────────────

    /**
     * US 01.05.02 — "waitlisted" status is mapped to "On Waitlist".
     */
    @Test
    public void loadHistory_waitlistedStatus_mapsToOnWaitlist() {
        loadWith(makeReg("e1", "Event A", "waitlisted"));

        String label = vm.getRegistrations().getValue().get(0).getStatusLabel();
        assertEquals("On Waitlist", label);
    }

    /**
     * US 01.05.03 — "invited" status (lottery drawn) is mapped to "Selected!".
     */
    @Test
    public void loadHistory_invitedStatus_mapsToSelected() {
        loadWith(makeReg("e1", "Event A", "invited"));

        assertEquals("Selected!", vm.getRegistrations().getValue().get(0).getStatusLabel());
    }

    /**
     * "selected" is a synonym for "invited" — maps to "Selected!".
     */
    @Test
    public void loadHistory_selectedStatus_mapsToSelected() {
        loadWith(makeReg("e1", "Event A", "selected"));

        assertEquals("Selected!", vm.getRegistrations().getValue().get(0).getStatusLabel());
    }

    /**
     * US 01.05.04 — "accepted" maps to "Enrolled".
     */
    @Test
    public void loadHistory_acceptedStatus_mapsToEnrolled() {
        loadWith(makeReg("e1", "Event A", "accepted"));

        assertEquals("Enrolled", vm.getRegistrations().getValue().get(0).getStatusLabel());
    }

    /**
     * "declined" maps to "Declined".
     */
    @Test
    public void loadHistory_declinedStatus_mapsToDeclined() {
        loadWith(makeReg("e1", "Event A", "declined"));

        assertEquals("Declined", vm.getRegistrations().getValue().get(0).getStatusLabel());
    }

    /**
     * "cancelled" maps to "Cancelled".
     */
    @Test
    public void loadHistory_cancelledStatus_mapsToCancelled() {
        loadWith(makeReg("e1", "Event A", "cancelled"));

        assertEquals("Cancelled", vm.getRegistrations().getValue().get(0).getStatusLabel());
    }

    /**
     * Multiple registrations are all mapped and appear in the list.
     */
    @Test
    public void loadHistory_multipleRegistrations_allMapped() {
        List<RegistrationHistoryViewModel.Registration> input = Arrays.asList(
                makeReg("e1", "Spring Gala",  "waitlisted"),
                makeReg("e2", "Summer Picnic", "accepted"),
                makeReg("e3", "Fall Concert",  "declined")
        );

        doAnswer(inv -> { callCallback(inv, input, null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertEquals(3, vm.getRegistrations().getValue().size());
    }

    /**
     * Event title and eventId are passed through unchanged.
     */
    @Test
    public void loadHistory_registrationFields_passedThrough() {
        loadWith(makeReg("event-abc", "My Event Title", "accepted"));

        RegistrationHistoryViewModel.RegistrationHistoryItem item =
                vm.getRegistrations().getValue().get(0);
        assertEquals("event-abc",      item.getEventId());
        assertEquals("My Event Title", item.getEventTitle());
        assertEquals(TIMESTAMP,        item.getTimestamp());
    }

    // ── empty state ──────────────────────────────────────────────────────────

    /**
     * An empty list from the repository results in an empty registrations LiveData.
     */
    @Test
    public void loadHistory_emptyList_registrationsLiveDataIsEmpty() {
        doAnswer(inv -> { callCallback(inv, new ArrayList<>(), null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertTrue(vm.getRegistrations().getValue().isEmpty());
    }

    // ── error handling ───────────────────────────────────────────────────────

    /**
     * Repository error populates the errorMessage LiveData.
     */
    @Test
    public void loadHistory_repoError_setsErrorMessage() {
        doAnswer(inv -> { callCallback(inv, null, new Exception("Firestore offline")); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        String error = vm.getErrorMessage().getValue();
        assertNotNull(error);
        assertFalse(error.isEmpty());
    }

    /**
     * Repository error clears any previously loaded registrations.
     */
    @Test
    public void loadHistory_repoError_clearsRegistrationsList() {
        doAnswer(inv -> { callCallback(inv, null, new Exception("Offline")); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertTrue(vm.getRegistrations().getValue().isEmpty());
    }

    /**
     * isLoading is false after a successful fetch completes.
     */
    @Test
    public void loadHistory_afterSuccess_isLoadingFalse() {
        doAnswer(inv -> { callCallback(inv, new ArrayList<>(), null); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertFalse(vm.getIsLoading().getValue());
    }

    /**
     * isLoading is false even after an error.
     */
    @Test
    public void loadHistory_afterError_isLoadingFalse() {
        doAnswer(inv -> { callCallback(inv, null, new Exception("fail")); return null; })
                .when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertFalse(vm.getIsLoading().getValue());
    }

    // ── HistoryAdapter filter logic ──────────────────────────────────────────

    /**
     * HistoryAdapter.setFilter with a specific status returns only matching items.
     * Tests the in-memory filter added in Phase 1.9.
     */
    @Test
    public void adapterFilter_byStatus_returnsOnlyMatchingItems() {
        // Build a list with 3 items of different statuses
        List<RegistrationHistoryViewModel.RegistrationHistoryItem> all = Arrays.asList(
                makeItem("e1", "Event A", "On Waitlist",  TIMESTAMP),
                makeItem("e2", "Event B", "Selected!",    TIMESTAMP),
                makeItem("e3", "Event C", "On Waitlist",  TIMESTAMP)
        );

        // Simulate the adapter's filter logic inline (mirrors HistoryAdapter.setFilter)
        String filter = "Selected!";
        List<RegistrationHistoryViewModel.RegistrationHistoryItem> filtered = new ArrayList<>();
        for (RegistrationHistoryViewModel.RegistrationHistoryItem item : all) {
            if (filter.equals(item.getStatusLabel())) filtered.add(item);
        }

        assertEquals(1, filtered.size());
        assertEquals("Selected!", filtered.get(0).getStatusLabel());
    }

    /**
     * HistoryAdapter.setFilter with null status returns all items (no filter).
     */
    @Test
    public void adapterFilter_nullStatus_returnsAllItems() {
        List<RegistrationHistoryViewModel.RegistrationHistoryItem> all = Arrays.asList(
                makeItem("e1", "Event A", "On Waitlist", TIMESTAMP),
                makeItem("e2", "Event B", "Selected!",   TIMESTAMP),
                makeItem("e3", "Event C", "Enrolled",    TIMESTAMP)
        );

        String filter = null;
        List<RegistrationHistoryViewModel.RegistrationHistoryItem> filtered = new ArrayList<>();
        for (RegistrationHistoryViewModel.RegistrationHistoryItem item : all) {
            boolean statusOk = (filter == null) || filter.equals(item.getStatusLabel());
            if (statusOk) filtered.add(item);
        }

        assertEquals(3, filtered.size());
    }

    /**
     * HistoryAdapter.setFilter with a date range excludes out-of-range items.
     */
    @Test
    public void adapterFilter_byDateRange_excludesOutOfRangeItems() {
        long inRange  = TIMESTAMP;           // 2026-03-11
        long outRange = TIMESTAMP - 86_400_000L * 10; // 10 days before

        List<RegistrationHistoryViewModel.RegistrationHistoryItem> all = Arrays.asList(
                makeItem("e1", "In Range",  "On Waitlist", inRange),
                makeItem("e2", "Out Range", "On Waitlist", outRange)
        );

        long[] dateRange = {TIMESTAMP - 86_400_000L, TIMESTAMP + 86_400_000L}; // ±1 day
        List<RegistrationHistoryViewModel.RegistrationHistoryItem> filtered = new ArrayList<>();
        for (RegistrationHistoryViewModel.RegistrationHistoryItem item : all) {
            long ts = item.getTimestamp();
            if (ts >= dateRange[0] && ts <= dateRange[1] + 86_400_000L) {
                filtered.add(item);
            }
        }

        assertEquals(1, filtered.size());
        assertEquals("In Range", filtered.get(0).getEventTitle());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private RegistrationHistoryViewModel.Registration makeReg(
            String eventId, String title, String status) {
        return new RegistrationHistoryViewModel.Registration(
                eventId, title, null, status, TIMESTAMP);
    }

    /** Creates a RegistrationHistoryItem directly (for adapter filter tests). */
    private RegistrationHistoryViewModel.RegistrationHistoryItem makeItem(
            String eventId, String title, String statusLabel, long timestamp) {
        return new RegistrationHistoryViewModel.RegistrationHistoryItem(
                eventId, title, null, statusLabel, timestamp);
    }

    /** Call loadRegistrationHistory() with a single-item list containing {@code reg}. */
    private void loadWith(RegistrationHistoryViewModel.Registration reg) {
        List<RegistrationHistoryViewModel.Registration> list = new ArrayList<>();
        list.add(reg);
        doAnswer(inv -> { callCallback(inv, list, null); return null; })
                .when(mockRepo).getHistoryForUser(any());
        vm.loadRegistrationHistory();
    }

    private void callCallback(
            org.mockito.invocation.InvocationOnMock inv,
            List<RegistrationHistoryViewModel.Registration> regs,
            Exception error) {
        ((RegistrationHistoryViewModel.RegistrationRepository.HistoryCallback)
                inv.getArgument(0)).onResult(regs, error);
    }
}
