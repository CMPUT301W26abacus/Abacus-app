package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MainHistoryViewModel.
 *
 * Covers:
 *   US 01.02.03 — View Registration History
 *
 * The mock type is MainHistoryViewModel.RegistrationRepository (the inner
 * interface), NOT the RegistrationRepository class — matching the actual
 * constructor signature.
 *
 * InstantTaskExecutorRule makes LiveData.postValue() synchronous.
 */
@RunWith(MockitoJUnitRunner.class)
public class MainHistoryViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    // Inner interface — matches the actual constructor parameter type
    @Mock MainHistoryViewModel.RegistrationRepository mockRepo;

    private MainHistoryViewModel vm;

    // Realistic timestamps (epoch ms)
    private static final long TS_SPRING_GALA    = 1741694400000L; // 2026-03-11 12:00 UTC
    private static final long TS_WINTER_CONCERT = 1734998400000L; // 2024-12-24 00:00 UTC
    private static final long TS_HACKATHON      = 1738368000000L; // 2025-02-01 00:00 UTC

    @Before
    public void setUp() {
        vm = new MainHistoryViewModel(mockRepo);
    }

    // ── loadRegistrationHistory — success cases ───────────────────────────────

    /**
     * US 01.02.03 — Multiple registrations are loaded and exposed via LiveData.
     */
    @Test
    public void loadHistory_multipleRegistrations_allItemsExposed() {
        List<MainHistoryViewModel.Registration> regs = Arrays.asList(
                new MainHistoryViewModel.Registration("Spring Gala 2026",  "waitlisted", TS_SPRING_GALA),
                new MainHistoryViewModel.Registration("Winter Concert",     "accepted",   TS_WINTER_CONCERT),
                new MainHistoryViewModel.Registration("CS Hackathon 2025", "declined",   TS_HACKATHON)
        );
        stubSuccess(regs);

        vm.loadRegistrationHistory();

        List<MainHistoryViewModel.RegistrationHistoryItem> items = vm.getRegistrations().getValue();
        assertNotNull(items);
        assertEquals(3, items.size());
    }

    /**
     * US 01.02.03 — Event titles are preserved exactly in history items.
     */
    @Test
    public void loadHistory_eventTitlesPreserved() {
        List<MainHistoryViewModel.Registration> regs = Collections.singletonList(
                new MainHistoryViewModel.Registration("Spring Gala 2026", "waitlisted", TS_SPRING_GALA)
        );
        stubSuccess(regs);

        vm.loadRegistrationHistory();

        assertEquals("Spring Gala 2026",
                vm.getRegistrations().getValue().get(0).getEventTitle());
    }

    /**
     * US 01.02.03 — Registration timestamps are preserved in history items.
     */
    @Test
    public void loadHistory_timestampsPreserved() {
        List<MainHistoryViewModel.Registration> regs = Collections.singletonList(
                new MainHistoryViewModel.Registration("Spring Gala 2026", "waitlisted", TS_SPRING_GALA)
        );
        stubSuccess(regs);

        vm.loadRegistrationHistory();

        assertEquals(TS_SPRING_GALA,
                vm.getRegistrations().getValue().get(0).getTimestamp());
    }

    /**
     * US 01.02.03 — Empty list (no prior registrations) shows empty state, no error.
     */
    @Test
    public void loadHistory_noRegistrations_emptyList() {
        stubSuccess(Collections.emptyList());

        vm.loadRegistrationHistory();

        List<MainHistoryViewModel.RegistrationHistoryItem> items = vm.getRegistrations().getValue();
        assertNotNull(items);
        assertTrue(items.isEmpty());
        // No error message for an empty list
        String err = vm.getErrorMessage().getValue();
        assertTrue(err == null || err.isEmpty());
    }

    // ── Status label mapping — US 01.02.03 ───────────────────────────────────

    /** "waitlisted" → "On Waitlist" */
    @Test
    public void statusMapping_waitlisted_displaysOnWaitlist() {
        assertDisplayLabel("waitlisted", "On Waitlist");
    }

    /** "invited" / "selected" → "Selected!" */
    @Test
    public void statusMapping_selected_displaysSelected() {
        assertDisplayLabel("selected", "Selected!");
    }

    /** "accepted" → "Enrolled" */
    @Test
    public void statusMapping_accepted_displaysEnrolled() {
        assertDisplayLabel("accepted", "Enrolled");
    }

    /** "declined" → "Declined" */
    @Test
    public void statusMapping_declined_displaysDeclined() {
        assertDisplayLabel("declined", "Declined");
    }

    /** "cancelled" → "Cancelled" */
    @Test
    public void statusMapping_cancelled_displaysCancelled() {
        assertDisplayLabel("cancelled", "Cancelled");
    }

    /** null status → "Unknown" (guard against bad Firestore data). */
    @Test
    public void statusMapping_null_displaysUnknown() {
        assertDisplayLabel(null, "Unknown");
    }

    /** Unrecognised status passes through unchanged (forward-compatible). */
    @Test
    public void statusMapping_unknownStatus_passesThrough() {
        assertDisplayLabel("pending_review", "pending_review");
    }

    // ── isLoading lifecycle ───────────────────────────────────────────────────

    /** isLoading is false after a successful load. */
    @Test
    public void loadHistory_success_isLoadingFalseAfterLoad() {
        stubSuccess(Collections.emptyList());

        vm.loadRegistrationHistory();

        assertFalse(vm.getIsLoading().getValue());
    }

    /** isLoading is false even after a failed load. */
    @Test
    public void loadHistory_failure_isLoadingFalseAfterLoad() {
        stubError(new Exception("Firestore unavailable"));

        vm.loadRegistrationHistory();

        assertFalse(vm.getIsLoading().getValue());
    }

    // ── Error handling ────────────────────────────────────────────────────────

    /**
     * US 01.02.03 — A Firestore error posts an error message and an empty list
     *               (so the UI shows empty state instead of stale data).
     */
    @Test
    public void loadHistory_repoError_postsErrorMessageAndEmptyList() {
        stubError(new Exception("Firestore unavailable"));

        vm.loadRegistrationHistory();

        String error = vm.getErrorMessage().getValue();
        assertNotNull(error);
        assertFalse(error.isEmpty());

        List<MainHistoryViewModel.RegistrationHistoryItem> items = vm.getRegistrations().getValue();
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    /**
     * US 01.02.03 — A null result (no error, but null list) also posts an error
     *               to avoid a NullPointerException downstream.
     */
    @Test
    public void loadHistory_nullResult_postsErrorMessageAndEmptyList() {
        doAnswer(inv -> {
            ((MainHistoryViewModel.RegistrationRepository.HistoryCallback) inv.getArgument(0))
                    .onResult(null, null);
            return null;
        }).when(mockRepo).getHistoryForUser(any());

        vm.loadRegistrationHistory();

        assertNotNull(vm.getErrorMessage().getValue());
        assertFalse(vm.getErrorMessage().getValue().isEmpty());
    }

    // ── refresh ───────────────────────────────────────────────────────────────

    /**
     * US 01.02.03 — refresh() triggers a second load, allowing pull-to-refresh.
     */
    @Test
    public void refresh_callsRepositorySecondTime() {
        stubSuccess(Collections.emptyList());

        vm.loadRegistrationHistory();
        vm.refresh();

        verify(mockRepo, times(2)).getHistoryForUser(any());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void stubSuccess(List<MainHistoryViewModel.Registration> regs) {
        doAnswer(inv -> {
            ((MainHistoryViewModel.RegistrationRepository.HistoryCallback) inv.getArgument(0))
                    .onResult(regs, null);
            return null;
        }).when(mockRepo).getHistoryForUser(any());
    }

    private void stubError(Exception e) {
        doAnswer(inv -> {
            ((MainHistoryViewModel.RegistrationRepository.HistoryCallback) inv.getArgument(0))
                    .onResult(null, e);
            return null;
        }).when(mockRepo).getHistoryForUser(any());
    }

    /** Load a single registration with the given status and assert the display label. */
    private void assertDisplayLabel(String rawStatus, String expectedLabel) {
        stubSuccess(Collections.singletonList(
                new MainHistoryViewModel.Registration("Test Event", rawStatus, TS_SPRING_GALA)));

        // Fresh ViewModel to avoid state leak between parameterised calls
        MainHistoryViewModel fresh = new MainHistoryViewModel(mockRepo);
        fresh.loadRegistrationHistory();

        List<MainHistoryViewModel.RegistrationHistoryItem> items =
                fresh.getRegistrations().getValue();
        assertNotNull(items);
        assertFalse(items.isEmpty());
        assertEquals(expectedLabel, items.get(0).getStatusLabel());
    }
}