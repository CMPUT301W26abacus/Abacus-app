package com.example.abacus_app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.os.Handler;
import android.os.Looper;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for CreateEventViewModel.
 * Handles mocking of Firebase and Android Looper/Handler.
 */
@RunWith(MockitoJUnitRunner.class)
public class CreateEventViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private CreateEventViewModel viewModel;
    private MockedStatic<FirebaseFirestore> mockedFirestore;
    private MockedStatic<Looper> mockedLooper;
    private MockedConstruction<Handler> mockedHandler;

    @Mock
    private Looper mockLooper;

    @Before
    public void setUp() {
        // Mock Firestore
        mockedFirestore = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(mock(FirebaseFirestore.class));
        
        // Mock Looper
        mockedLooper = Mockito.mockStatic(Looper.class);
        mockedLooper.when(Looper::getMainLooper).thenReturn(mockLooper);

        // Mock Handler to run posted tasks immediately
        mockedHandler = Mockito.mockConstruction(Handler.class, (mock, context) -> {
            when(mock.post(any(Runnable.class))).thenAnswer(invocation -> {
                Runnable runnable = invocation.getArgument(0);
                runnable.run();
                return true;
            });
        });

        viewModel = new CreateEventViewModel();
    }

    @After
    public void tearDown() {
        if (mockedFirestore != null) mockedFirestore.close();
        if (mockedLooper != null) mockedLooper.close();
        if (mockedHandler != null) mockedHandler.close();
    }

    /**
     * Checks that the initial state of the view model is correct.
     */
    @Test
    public void testInitialState() {
        assertNotNull(viewModel.getIsSaving());
        assertNotNull(viewModel.getEventCreated());
        assertNotNull(viewModel.getError());

        assertFalse(viewModel.getIsSaving().getValue());
        assertFalse(viewModel.getEventCreated().getValue());
        assertNull(viewModel.getError().getValue());
    }
}
