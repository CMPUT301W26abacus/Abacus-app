package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ManageEventViewModel}.
 *
 * <p>Firebase is mocked statically so the ViewModel can be constructed without a
 * real Firestore connection. Tests that exercise Firestore reads/writes belong in
 * instrumented tests (androidTest).
 *
 * @see ManageEventViewModel
 */
@RunWith(MockitoJUnitRunner.class)
public class ManageEventViewModelTest {

    /**
     * Makes LiveData {@code setValue()} calls execute synchronously on the test thread.
     */
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ManageEventViewModel viewModel;

    @Before
    public void setUp() {
        // Stub FirebaseFirestore.getInstance() so the ViewModel field initialiser
        // does not require a real Firebase project during unit tests.
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            mockedFirestore.when(FirebaseFirestore::getInstance)
                    .thenReturn(mock(FirebaseFirestore.class));
            viewModel = new ManageEventViewModel();
        }
    }

    // ── Initial LiveData state ────────────────────────────────────────────────

    /**
     * Verifies that all LiveData fields start with sensible default values so observers
     * do not receive null on first subscription.
     */
    @Test
    public void testInitialLiveDataDefaults() {
        assertNotNull(viewModel.getEntrants().getValue());
        assertNotNull(viewModel.getEvents().getValue());
        assertNotNull(viewModel.getSearchResults().getValue());
        assertNotNull(viewModel.getCoOrganizers().getValue());

        assertEquals(0, viewModel.getEntrants().getValue().size());
        assertEquals(0, viewModel.getEvents().getValue().size());
        assertEquals(0, viewModel.getSearchResults().getValue().size());
        assertEquals(0, viewModel.getCoOrganizers().getValue().size());

        assertFalse(viewModel.getIsLoading().getValue());
        assertFalse(viewModel.getLotteryCompleted().getValue());
        assertFalse(viewModel.getEventDeleted().getValue());
        assertNull(viewModel.getError().getValue());
        assertNull(viewModel.getEventDetails().getValue());
    }

    // ── loadOrganizerEvents — ID aggregation logic ────────────────────────────

    /**
     * When both {@code deviceUuid} and {@code firebaseUid} are null,
     * {@link ManageEventViewModel#loadOrganizerEvents(String, String)} must post an error
     * without making a Firestore query.
     */
    @Test
    public void testLoadOrganizerEvents_bothIdsNull_setsError() {
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            mockedFirestore.when(FirebaseFirestore::getInstance)
                    .thenReturn(mock(FirebaseFirestore.class));

            viewModel.loadOrganizerEvents(null, null);

            assertEquals("No organizer ID found", viewModel.getError().getValue());
            assertFalse(viewModel.getIsLoading().getValue());
        }
    }

    /**
     * When only {@code deviceUuid} is provided (firebaseUid is null), the method should
     * attempt a Firestore query without throwing. Loading state is set to true before the
     * async call, so we verify that the error LiveData is NOT set in this case.
     */
    @Test
    public void testLoadOrganizerEvents_deviceUuidOnly_doesNotSetError() {
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            FirebaseFirestore mockDb = mock(FirebaseFirestore.class);
            mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mockDb);

            // EventRepository will attempt a Firestore query; the mock returns null tasks,
            // so we only assert that the no-ID guard is not triggered.
            try {
                viewModel.loadOrganizerEvents("device-uuid-123", null);
            } catch (Exception ignored) {
                // NullPointerException from the mock Task is expected here; the point
                // is that the error message "No organizer ID found" was NOT set.
            }

            assertNull(viewModel.getError().getValue());
        }
    }

    /**
     * When only {@code firebaseUid} is provided (deviceUuid is null), the method should
     * likewise not set the no-ID error.
     */
    @Test
    public void testLoadOrganizerEvents_firebaseUidOnly_doesNotSetError() {
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            mockedFirestore.when(FirebaseFirestore::getInstance)
                    .thenReturn(mock(FirebaseFirestore.class));

            try {
                viewModel.loadOrganizerEvents(null, "firebase-uid-456");
            } catch (Exception ignored) { }

            assertNull(viewModel.getError().getValue());
        }
    }

    // ── searchUsersByEmail — pure input validation ────────────────────────────

    /**
     * Passing a null email must clear search results immediately without a Firestore query.
     */
    @Test
    public void testSearchUsersByEmail_null_clearsResults() {
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            mockedFirestore.when(FirebaseFirestore::getInstance)
                    .thenReturn(mock(FirebaseFirestore.class));

            viewModel.searchUsersByEmail(null);

            assertNotNull(viewModel.getSearchResults().getValue());
            assertEquals(0, viewModel.getSearchResults().getValue().size());
        }
    }

    /**
     * Passing a blank/whitespace-only email must also clear results immediately.
     */
    @Test
    public void testSearchUsersByEmail_blank_clearsResults() {
        try (MockedStatic<FirebaseFirestore> mockedFirestore =
                     Mockito.mockStatic(FirebaseFirestore.class)) {
            mockedFirestore.when(FirebaseFirestore::getInstance)
                    .thenReturn(mock(FirebaseFirestore.class));

            viewModel.searchUsersByEmail("   ");

            assertNotNull(viewModel.getSearchResults().getValue());
            assertEquals(0, viewModel.getSearchResults().getValue().size());
        }
    }
}
