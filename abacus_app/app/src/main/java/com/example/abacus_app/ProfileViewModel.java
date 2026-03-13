package com.example.abacus_app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;

import java.util.HashMap;
import java.util.Map;

/**
 * ProfileViewModel
 *
 * Holds all business logic and state for the profile screen.
 * Sits between ProfileFragment and UserRepository so the fragment
 * never touches data directly.
 *
 * Ref: US 01.02.01–04
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<String>  _name         = new MutableLiveData<>("");
    private final MutableLiveData<String>  _email        = new MutableLiveData<>("");
    private final MutableLiveData<String>  _phone        = new MutableLiveData<>("");
    private final MutableLiveData<String>  _role         = new MutableLiveData<>("entrant");
    private final MutableLiveData<Boolean> _notificationsEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _nameError    = new MutableLiveData<>(null);
    private final MutableLiveData<String>  _emailError   = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> _isSaving     = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _isGuest      = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _toastMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> _profileDeleted = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _logoutComplete = new MutableLiveData<>(false);

    public LiveData<String>  getName()                 { return _name; }
    public LiveData<String>  getEmail()                { return _email; }
    public LiveData<String>  getPhone()                { return _phone; }
    public LiveData<String>  getRole()                 { return _role; }
    public LiveData<Boolean> getNotificationsEnabled() { return _notificationsEnabled; }
    public LiveData<String>  getNameError()     { return _nameError; }
    public LiveData<String>  getEmailError()    { return _emailError; }
    public LiveData<Boolean> getIsSaving()      { return _isSaving; }
    public LiveData<Boolean> getIsGuest()       { return _isGuest; }
    public LiveData<String>  getToastMessage()  { return _toastMessage; }
    public LiveData<Boolean> getProfileDeleted(){ return _profileDeleted; }
    public LiveData<Boolean> getLogoutComplete() { return _logoutComplete; }
    private UserRepository userRepository;

    /**
     * Initializes the ViewModel with a UserRepository and guest status.
     * @param repository UserRepository instance
     * @param isGuest boolean indicating if the user is a guest
     */
    public void init(UserRepository repository, boolean isGuest) {
        this.userRepository = repository;
        _isGuest.setValue(isGuest);
    }

    /**
     * Sets the user name.
     * @param name String containing the user's name
     */
    public void setName(String name) {
        _name.setValue(name);
        _nameError.setValue(null);
    }
    /**
     * Sets the user email.
     * @param email String containing the user's email
     */
    public void setEmail(String email) {
        _email.setValue(email);
        _emailError.setValue(null);
    }

    /**
     * Sets the user phone number.
     * @param phone String containing the user's phone number
     */
    public void setPhone(String phone) {
        _phone.setValue(phone);
    }
    
    /**
     * Sets the user role.
     * @param role String containing the user's role
     */
    public void setRole(String role) {
        _role.setValue(role);
    }

    /**
     * Sets the notifications enabled state.
     * @param enabled Boolean indicating whether notifications are enabled
     */
    public void setNotificationsEnabled(boolean enabled) {
        _notificationsEnabled.setValue(enabled);
    }

    /**
     * Loads the user profile from Firestore and pushes values into LiveData.
     * Called by the fragment in onViewCreated().
     */
    public void loadProfile() {
        if (userRepository == null) return;

        userRepository.getProfileAsync(user -> {
            if (user != null) {
                _name.postValue(user.getName() != null  ? user.getName()  : "");
                _email.postValue(user.getEmail() != null ? user.getEmail() : "");
                _phone.postValue(user.getPhone() != null ? user.getPhone() : "");
                _role.postValue(user.getRole() != null ? user.getRole() : "entrant");
                _notificationsEnabled.postValue(user.getNotificationsEnabled());

                boolean guest = user.isGuest() || user.getLastLoginAt() == 0;
                _isGuest.postValue(guest);
            }
        });
    }

    /**
     * Validates inputs then saves name, email, phone, and notificationsEnabled to Firestore.
     * Role is intentionally excluded — it can only be changed via Firestore console
     * or by an admin, never by the user editing their own profile.
     */
    public void saveProfile() {
        if (userRepository == null) return;

        String name  = _name.getValue()  != null ? _name.getValue().trim()  : "";
        String email = _email.getValue() != null ? _email.getValue().trim() : "";
        String phone = _phone.getValue() != null ? _phone.getValue().trim() : "";
        boolean notificationsEnabled = _notificationsEnabled.getValue() != null
                ? _notificationsEnabled.getValue() : true;

        if (name.isEmpty()) {
            _nameError.setValue("Name cannot be empty");
            return;
        }

        if (!email.isEmpty() && (!email.contains("@") || !email.contains("."))) {
            _emailError.setValue("Please enter a valid email address");
            return;
        }

        _isSaving.setValue(true);

        Map<String, Object> data = new HashMap<>();
        data.put("name",  name);
        data.put("email", email);
        data.put("phone", phone);
        data.put("notificationsEnabled", notificationsEnabled);
        // role is NOT included here — saving profile never overwrites role

        userRepository.saveProfileAsync(data, error -> {
            _isSaving.postValue(false);
            if (error == null) {
                _toastMessage.postValue("Profile saved!");
            } else {
                _toastMessage.postValue("Error saving profile: " + error.getMessage());
            }
        });
    }

    /**
     * Soft-deletes the profile via the repository.
     * Fragment should call this only after the user confirms via AlertDialog.
     */
    public void deleteProfile() {
        if (userRepository == null) return;

        userRepository.deleteProfileAsync(error -> {
            if (error == null) {
                _name.postValue("");
                _email.postValue("");
                _phone.postValue("");
                _profileDeleted.postValue(true);
                _toastMessage.postValue("Profile deleted");
            } else {
                _toastMessage.postValue("Error deleting profile: " + error.getMessage());
            }
        });
    }

    /**
     * Clears the local UUID, signs out of Firebase Auth, and signals the
     * fragment to navigate to LoginActivity. The next app launch will start
     * fresh with a new anonymous identity rather than reloading the old profile.
     */
    public void logout() {
        if (userRepository != null) {
            userRepository.clearLocalSession();
        } else {
            FirebaseAuth.getInstance().signOut();
        }
        _logoutComplete.setValue(true);
    }

    public void clearToast() {
        _toastMessage.setValue(null);
    }
}