package com.example.abacus_app;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProfileViewModel}.
 *
 * Covers:
 *   US 01.02.01 — Create profile: name/email/phone input and field validation
 *   US 01.02.02 — Update profile: field updates, save success and failure
 *   US 01.02.04 — Delete profile: soft-delete, field clearing, navigation signal
 *
 * Runs on the JVM — no device or emulator required.
 * {@link InstantTaskExecutorRule} makes LiveData deliver synchronously.
 *
 * Callback argument positions:
 *   saveProfileAsync(Map, VoidCallback)  → VoidCallback at index 1
 *   deleteProfileAsync(VoidCallback)     → VoidCallback at index 0
 *   getProfileAsync(UserCallback)        → UserCallback at index 0
 */
@RunWith(MockitoJUnitRunner.class)
public class ProfileViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock UserRepository mockRepo;

    private ProfileViewModel vm;

    @Before
    public void setUp() {
        vm = new ProfileViewModel();
        vm.init(mockRepo, /* isGuest= */ true);
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test
    public void initialState_allTextFieldsEmpty() {
        assertEquals("", vm.getName().getValue());
        assertEquals("", vm.getEmail().getValue());
        assertEquals("", vm.getPhone().getValue());
    }

    @Test
    public void initialState_noValidationErrors() {
        assertNull(vm.getNameError().getValue());
        assertNull(vm.getEmailError().getValue());
    }

    @Test
    public void initialState_isNotSaving() {
        assertFalse(vm.getIsSaving().getValue());
    }

    @Test
    public void init_withGuestTrue_isGuestLiveDataIsTrue() {
        assertTrue(vm.getIsGuest().getValue());
    }

    @Test
    public void init_withGuestFalse_isGuestLiveDataIsFalse() {
        ProfileViewModel vm2 = new ProfileViewModel();
        vm2.init(mockRepo, false);
        assertFalse(vm2.getIsGuest().getValue());
    }

    // ── Field setters — US 01.02.01 ──────────────────────────────────────────

    @Test
    public void setName_updatesNameLiveData() {
        vm.setName("Alice Smith");
        assertEquals("Alice Smith", vm.getName().getValue());
    }

    @Test
    public void setEmail_updatesEmailLiveData() {
        vm.setEmail("alice@ualberta.ca");
        assertEquals("alice@ualberta.ca", vm.getEmail().getValue());
    }

    @Test
    public void setPhone_updatesPhoneLiveData() {
        vm.setPhone("780-492-3111");
        assertEquals("780-492-3111", vm.getPhone().getValue());
    }

    @Test
    public void setRole_updatesRoleLiveData() {
        vm.setRole("organizer");
        assertEquals("organizer", vm.getRole().getValue());
    }

    @Test
    public void setNotificationsEnabled_updatesLiveData() {
        vm.setNotificationsEnabled(false);
        assertFalse(vm.getNotificationsEnabled().getValue());
    }

    @Test
    public void setName_afterValidationError_clearsNameError() {
        vm.setName(""); vm.saveProfile();
        assertNotNull(vm.getNameError().getValue());

        vm.setName("Alice Smith");
        assertNull(vm.getNameError().getValue());
    }

    @Test
    public void setEmail_afterValidationError_clearsEmailError() {
        vm.setName("Alice");
        vm.setEmail("bad-email"); vm.saveProfile();
        assertNotNull(vm.getEmailError().getValue());

        vm.setEmail("alice@ualberta.ca");
        assertNull(vm.getEmailError().getValue());
    }

    // ── saveProfile validation — US 01.02.01 ─────────────────────────────────

    @Test
    public void saveProfile_emptyName_setsNameError() {
        vm.setName("");
        vm.saveProfile();

        assertNotNull(vm.getNameError().getValue());
        verify(mockRepo, never()).saveProfileAsync(anyMap(), any());
    }

    @Test
    public void saveProfile_whitespaceOnlyName_setsNameError() {
        vm.setName("   ");
        vm.saveProfile();

        assertNotNull(vm.getNameError().getValue());
    }

    @Test
    public void saveProfile_emailWithoutAt_setsEmailError() {
        vm.setName("Alice Smith");
        vm.setEmail("aliceualberta.ca");
        vm.saveProfile();

        assertNotNull(vm.getEmailError().getValue());
        verify(mockRepo, never()).saveProfileAsync(anyMap(), any());
    }

    @Test
    public void saveProfile_emailWithoutDot_setsEmailError() {
        vm.setName("Alice Smith");
        vm.setEmail("alice@ualberta");
        vm.saveProfile();

        assertNotNull(vm.getEmailError().getValue());
    }

    @Test
    public void saveProfile_emptyEmail_isValid_noEmailError() {
        vm.setName("Alice Smith");
        vm.setEmail("");
        doAnswer(inv -> { callSaveCallback(inv, null); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();

        assertNull(vm.getEmailError().getValue());
        verify(mockRepo).saveProfileAsync(anyMap(), any());
    }

    // ── saveProfile success / failure — US 01.02.02 ──────────────────────────

    @Test
    public void saveProfile_validInputs_callsRepository() {
        vm.setName("Alice Smith");
        vm.setEmail("alice@ualberta.ca");
        vm.setPhone("780-492-3111");
        doAnswer(inv -> { callSaveCallback(inv, null); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();

        verify(mockRepo).saveProfileAsync(anyMap(), any());
    }

    @Test
    public void saveProfile_success_postsProfileSavedToast() {
        vm.setName("Alice Smith");
        doAnswer(inv -> { callSaveCallback(inv, null); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();

        assertEquals("Profile saved!", vm.getToastMessage().getValue());
    }

    @Test
    public void saveProfile_failure_postsErrorToast() {
        vm.setName("Alice Smith");
        doAnswer(inv -> { callSaveCallback(inv, new Exception("Firestore offline")); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();

        String toast = vm.getToastMessage().getValue();
        assertNotNull(toast);
        assertTrue(toast.startsWith("Error"));
    }

    @Test
    public void saveProfile_afterCompletion_isNotSaving() {
        vm.setName("Alice Smith");
        doAnswer(inv -> { callSaveCallback(inv, null); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();

        assertFalse(vm.getIsSaving().getValue());
    }

    @Test
    public void saveProfile_doesNotIncludeRoleField() {
        vm.setName("Alice Smith");
        doAnswer(inv -> {
            Map<String, Object> data = inv.getArgument(0);
            assertFalse("role must not be in save payload", data.containsKey("role"));
            callSaveCallback(inv, null);
            return null;
        }).when(mockRepo).saveProfileAsync(anyMap(), any());

        vm.saveProfile();
        verify(mockRepo).saveProfileAsync(anyMap(), any());
    }

    // ── loadProfile — US 01.02.02 ────────────────────────────────────────────

    @Test
    public void loadProfile_populatesAllFields() {
        User bob = new User(
                "550e8400-e29b-41d4-a716-446655440000",
                "bob.johnson@ualberta.ca",
                "Bob Johnson",
                "2026-02-10");
        bob.setPhone("587-123-4567");

        doAnswer(inv -> { callUserCallback(inv, bob); return null; })
                .when(mockRepo).getProfileAsync(any());

        vm.loadProfile();

        assertEquals("Bob Johnson",             vm.getName().getValue());
        assertEquals("bob.johnson@ualberta.ca", vm.getEmail().getValue());
        assertEquals("587-123-4567",            vm.getPhone().getValue());
    }

    @Test
    public void loadProfile_nullUser_doesNotCrash() {
        doAnswer(inv -> { callUserCallback(inv, null); return null; })
                .when(mockRepo).getProfileAsync(any());

        vm.loadProfile(); // should not throw
    }

    // ── deleteProfile — US 01.02.04 ──────────────────────────────────────────

    @Test
    public void deleteProfile_success_setsProfileDeletedTrue() {
        doAnswer(inv -> { callDeleteCallback(inv, null); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertTrue(vm.getProfileDeleted().getValue());
    }

    @Test
    public void deleteProfile_success_clearsNameField() {
        vm.setName("Alice Smith");
        doAnswer(inv -> { callDeleteCallback(inv, null); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertEquals("", vm.getName().getValue());
    }

    @Test
    public void deleteProfile_success_clearsEmailField() {
        vm.setEmail("alice@ualberta.ca");
        doAnswer(inv -> { callDeleteCallback(inv, null); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertEquals("", vm.getEmail().getValue());
    }

    @Test
    public void deleteProfile_success_clearsPhoneField() {
        vm.setPhone("780-492-3111");
        doAnswer(inv -> { callDeleteCallback(inv, null); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertEquals("", vm.getPhone().getValue());
    }

    @Test
    public void deleteProfile_success_postsProfileDeletedToast() {
        doAnswer(inv -> { callDeleteCallback(inv, null); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertEquals("Profile deleted", vm.getToastMessage().getValue());
    }

    @Test
    public void deleteProfile_failure_postsErrorToast() {
        doAnswer(inv -> { callDeleteCallback(inv, new Exception("Auth error")); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        String toast = vm.getToastMessage().getValue();
        assertNotNull(toast);
        assertTrue(toast.startsWith("Error"));
    }

    @Test
    public void deleteProfile_failure_doesNotSetProfileDeletedTrue() {
        doAnswer(inv -> { callDeleteCallback(inv, new Exception("Auth error")); return null; })
                .when(mockRepo).deleteProfileAsync(any());

        vm.deleteProfile();

        assertFalse(vm.getProfileDeleted().getValue());
    }

    // ── clearToast ───────────────────────────────────────────────────────────

    @Test
    public void clearToast_setsToastToNull() {
        vm.setName("Alice Smith");
        doAnswer(inv -> { callSaveCallback(inv, null); return null; })
                .when(mockRepo).saveProfileAsync(anyMap(), any());
        vm.saveProfile();
        assertNotNull(vm.getToastMessage().getValue());

        vm.clearToast();

        assertNull(vm.getToastMessage().getValue());
    }

    // ── logout ───────────────────────────────────────────────────────────────

    @Test
    public void logout_clearsNameEmailPhone() {
        vm.setName("Alice");
        vm.setEmail("alice@ualberta.ca");
        vm.setPhone("780-111-2222");

        vm.logout();

        assertEquals("", vm.getName().getValue());
        assertEquals("", vm.getEmail().getValue());
        assertEquals("", vm.getPhone().getValue());
    }

    @Test
    public void logout_setsIsGuestTrue() {
        vm = new ProfileViewModel();
        vm.init(mockRepo, false); // start logged in

        vm.logout();

        assertTrue(vm.getIsGuest().getValue());
    }

    @Test
    public void logout_callsClearLocalSessionOnRepository() {
        vm.logout();
        verify(mockRepo).clearLocalSession();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void callSaveCallback(org.mockito.invocation.InvocationOnMock inv, Exception error) {
        ((UserRepository.VoidCallback) inv.getArgument(1)).onComplete(error);
    }

    private void callDeleteCallback(org.mockito.invocation.InvocationOnMock inv, Exception error) {
        ((UserRepository.VoidCallback) inv.getArgument(0)).onComplete(error);
    }

    private void callUserCallback(org.mockito.invocation.InvocationOnMock inv, User user) {
        ((UserRepository.UserCallback) inv.getArgument(0)).onResult(user);
    }
}
