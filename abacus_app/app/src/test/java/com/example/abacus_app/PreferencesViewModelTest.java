package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PreferencesViewModel}.
 *
 * Runs on the JVM — no device or emulator required.
 * {@link InstantTaskExecutorRule} makes LiveData deliver synchronously.
 */
@RunWith(MockitoJUnitRunner.class)
public class PreferencesViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock UserRepository mockRepo;

    private PreferencesViewModel vm;

    @Before
    public void setUp() {
        vm = new PreferencesViewModel();
        vm.init(mockRepo);
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    public void initialState_preferencesEmpty() {
        Map<String, Object> prefs = vm.getPreferences().getValue();
        assertNotNull(prefs);
        assertTrue(prefs.isEmpty());
    }

    @Test
    public void initialState_isNotSaving() {
        assertFalse(vm.getIsSaving().getValue());
    }

    @Test
    public void initialState_toastIsNull() {
        assertNull(vm.getToastMessage().getValue());
    }

    // ── loadPreferences ──────────────────────────────────────────────────────

    @Test
    public void loadPreferences_populatesLiveDataFromUser() {
        Map<String, Object> storedPrefs = new HashMap<>();
        storedPrefs.put("categories", Arrays.asList("Music", "Sports"));
        storedPrefs.put("locationRangeKm", 25);

        User alice = new User("uid", "a@b.com", "Alice", "2026-01-01");
        alice.setPreferences(storedPrefs);

        doAnswer(inv -> {
            ((UserRepository.UserCallback) inv.getArgument(0)).onResult(alice);
            return null;
        }).when(mockRepo).getProfileAsync(any());

        vm.loadPreferences();

        assertEquals(storedPrefs, vm.getPreferences().getValue());
    }

    @Test
    public void loadPreferences_nullUser_postsEmptyMap() {
        doAnswer(inv -> {
            ((UserRepository.UserCallback) inv.getArgument(0)).onResult(null);
            return null;
        }).when(mockRepo).getProfileAsync(any());

        vm.loadPreferences();

        assertNotNull(vm.getPreferences().getValue());
        assertTrue(vm.getPreferences().getValue().isEmpty());
    }

    @Test
    public void loadPreferences_nullPreferences_postsEmptyMap() {
        User alice = new User("uid", "a@b.com", "Alice", "2026-01-01");
        alice.setPreferences(null);

        doAnswer(inv -> {
            ((UserRepository.UserCallback) inv.getArgument(0)).onResult(alice);
            return null;
        }).when(mockRepo).getProfileAsync(any());

        vm.loadPreferences();

        assertNotNull(vm.getPreferences().getValue());
        assertTrue(vm.getPreferences().getValue().isEmpty());
    }

    // ── savePreferences ──────────────────────────────────────────────────────

    @Test
    public void savePreferences_setsSavingTrueThenFalse() {
        final boolean[] sawTrue = {false};

        doAnswer(inv -> {
            // isSaving should be true when the call starts
            sawTrue[0] = Boolean.TRUE.equals(vm.getIsSaving().getValue());
            ((UserRepository.VoidCallback) inv.getArgument(1)).onComplete(null);
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());

        vm.savePreferences(new HashMap<>());

        assertTrue("isSaving must have been true during save", sawTrue[0]);
        assertFalse("isSaving must be false after save", vm.getIsSaving().getValue());
    }

    @Test
    public void savePreferences_success_updatesPreferencesLiveData() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("eventSize", "small");

        doAnswer(inv -> {
            ((UserRepository.VoidCallback) inv.getArgument(1)).onComplete(null);
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());

        vm.savePreferences(prefs);

        assertEquals(prefs, vm.getPreferences().getValue());
    }

    @Test
    public void savePreferences_success_postsToastMessage() {
        doAnswer(inv -> {
            ((UserRepository.VoidCallback) inv.getArgument(1)).onComplete(null);
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());

        vm.savePreferences(new HashMap<>());

        assertEquals("Preferences saved!", vm.getToastMessage().getValue());
    }

    @Test
    public void savePreferences_error_postsErrorToast() {
        doAnswer(inv -> {
            ((UserRepository.VoidCallback) inv.getArgument(1))
                    .onComplete(new Exception("Firestore offline"));
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());

        vm.savePreferences(new HashMap<>());

        String toast = vm.getToastMessage().getValue();
        assertNotNull(toast);
        assertTrue(toast.contains("Error"));
    }

    @Test
    public void savePreferences_error_isNotSavingAfter() {
        doAnswer(inv -> {
            ((UserRepository.VoidCallback) inv.getArgument(1))
                    .onComplete(new Exception("Network error"));
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());

        vm.savePreferences(new HashMap<>());

        assertFalse(vm.getIsSaving().getValue());
    }

    // ── clearToast ───────────────────────────────────────────────────────────

    @Test
    public void clearToast_setsToastToNull() {
        doAnswer(inv -> {
            ((UserRepository.VoidCallback) inv.getArgument(1)).onComplete(null);
            return null;
        }).when(mockRepo).savePreferencesAsync(anyMap(), any());
        vm.savePreferences(new HashMap<>());
        assertNotNull(vm.getToastMessage().getValue());

        vm.clearToast();

        assertNull(vm.getToastMessage().getValue());
    }
}
