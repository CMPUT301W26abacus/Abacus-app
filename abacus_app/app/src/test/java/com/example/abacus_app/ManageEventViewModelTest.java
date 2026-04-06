package com.example.abacus_app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * These tests verify the ManageEventViewModel's initial state and input handling.
 * We mock the construction of all repositories to avoid triggering Android-specific
 * code (like Looper and Handler) that doesn't exist in unit tests.
 */
@RunWith(MockitoJUnitRunner.class)
public class ManageEventViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private ManageEventViewModel viewModel;
    private MockedStatic<FirebaseFirestore> mockedFirestore;
    private MockedConstruction<RegistrationRepository> mockedRegistrationRepo;
    private MockedConstruction<NotificationRepository> mockedNotificationRepo;
    private MockedConstruction<EventRepository> mockedEventRepo;

    /**
     * Set up the mocks before each test. We intercept the creation of Firestore
     * and the repositories to keep the test environment isolated and fast.
     */
    @Before
    public void setUp() {
        // Mock static Firebase access
        mockedFirestore = mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mock(FirebaseFirestore.class));

        // Mock the construction of the repositories used by the ViewModel.
        // This ensures the ViewModel uses "dummy" versions that won't crash.
        mockedRegistrationRepo = mockConstruction(RegistrationRepository.class);
        mockedNotificationRepo = mockConstruction(NotificationRepository.class);
        mockedEventRepo = mockConstruction(EventRepository.class);

        viewModel = new ManageEventViewModel();
    }

    /**
     * Clean up mocks after every test to ensure a fresh state for the next one.
     */
    @After
    public void tearDown() {
        if (mockedFirestore != null) mockedFirestore.close();
        if (mockedRegistrationRepo != null) mockedRegistrationRepo.close();
        if (mockedNotificationRepo != null) mockedNotificationRepo.close();
        if (mockedEventRepo != null) mockedEventRepo.close();
    }

    /**
     * Confirms that the ViewModel starts with empty lists and default values.
     */
    @Test
    public void testInitialLiveDataDefaults() {
        assertNotNull(viewModel.getEntrants());
        assertNotNull(viewModel.getEvents());
        assertNotNull(viewModel.getSearchResults());
        assertNotNull(viewModel.getCoOrganizers());

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

    /**
     * Checks that an error message is set when trying to load events without a user identity.
     */
    @Test
    public void testLoadOrganizerEvents_bothIdsNull_setsError() {
        viewModel.loadOrganizerEvents(null, null);
        assertEquals("No organizer ID found", viewModel.getError().getValue());
        assertFalse(viewModel.getIsLoading().getValue());
    }

    /**
     * Ensures that search results are reset if invalid email input is provided.
     */
    @Test
    public void testSearchUsersByEmail_invalidInput_clearsResults() {
        viewModel.searchUsersByEmail(null);
        assertEquals(0, viewModel.getSearchResults().getValue().size());

        viewModel.searchUsersByEmail("   ");
        assertEquals(0, viewModel.getSearchResults().getValue().size());
    }
}
