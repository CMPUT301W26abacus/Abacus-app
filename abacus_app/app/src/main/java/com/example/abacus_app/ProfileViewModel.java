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
 */
public class ProfileViewModel extends ViewModel {

    private final MutableLiveData<String>  _name         = new MutableLiveData<>("");
    private final MutableLiveData<String>  _email        = new MutableLiveData<>("");
    private final MutableLiveData<String>  _phone        = new MutableLiveData<>("");
    private final MutableLiveData<String>  _role         = new MutableLiveData<>("entrant");
    private final MutableLiveData<String>  _viewMode     = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> _notificationsEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _nameError    = new MutableLiveData<>(null);
    private final MutableLiveData<String>  _emailError   = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> _isSaving     = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> _isGuest      = new MutableLiveData<>(true);
    private final MutableLiveData<String>  _toastMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> _profileDeleted   = new MutableLiveData<>(false);
    private final MutableLiveData<String>  _bio              = new MutableLiveData<>("");
    private final MutableLiveData<String>  _profilePhotoUrl  = new MutableLiveData<>("");
    private final MutableLiveData<String>  _organizationName = new MutableLiveData<>("");
    private final MutableLiveData<Integer> _eventsCreated    = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> _totalRegistrations = new MutableLiveData<>(0);

    // Stats from dev branch
    private final MutableLiveData<Integer> _eventsJoined    = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> _eventsWon       = new MutableLiveData<>(0);

    public LiveData<String>  getName()                 { return _name; }
    public LiveData<String>  getEmail()                { return _email; }
    public LiveData<String>  getPhone()                { return _phone; }
    public LiveData<String>  getRole()                 { return _role; }
    public LiveData<String>  getViewMode()             { return _viewMode; }
    public LiveData<Boolean> getNotificationsEnabled() { return _notificationsEnabled; }
    public LiveData<String>  getNameError()            { return _nameError; }
    public LiveData<String>  getEmailError()           { return _emailError; }
    public LiveData<Boolean> getIsSaving()             { return _isSaving; }
    public LiveData<Boolean> getIsGuest()              { return _isGuest; }
    public LiveData<String>  getToastMessage()         { return _toastMessage; }
    public LiveData<Boolean> getProfileDeleted()       { return _profileDeleted; }
    public LiveData<String>  getBio()                  { return _bio; }
    public LiveData<String>  getProfilePhotoUrl()      { return _profilePhotoUrl; }
    public LiveData<String>  getOrganizationName()     { return _organizationName; }
    public LiveData<Integer> getEventsCreated()        { return _eventsCreated; }
    public LiveData<Integer> getTotalRegistrations()   { return _totalRegistrations; }
    public LiveData<Integer> getEventsJoined()         { return _eventsJoined; }
    public LiveData<Integer> getEventsWon()            { return _eventsWon; }

    private UserRepository userRepository;
    private boolean profileLoaded = false;

    public void init(UserRepository repository, boolean isGuest) {
        this.userRepository = repository;
        _isGuest.setValue(isGuest);
    }

    public void setName(String name) {
        _name.setValue(name);
        _nameError.setValue(null);
    }

    public void setEmail(String email) {
        _email.setValue(email);
        _emailError.setValue(null);
    }

    public void setPhone(String phone) {
        _phone.setValue(phone);
    }

    public void setBio(String bio)                         { _bio.setValue(bio); }
    public void setProfilePhotoUrl(String url)             { _profilePhotoUrl.setValue(url); }
    public void setOrganizationName(String organizationName) { _organizationName.setValue(organizationName); }

    public void setRole(String role) {
        _role.setValue(role);
    }

    public void setViewMode(String mode) {
        _viewMode.setValue(mode);
    }

    public void setNotificationsEnabled(boolean enabled) {
        _notificationsEnabled.setValue(enabled);
    }

    public void invalidateProfile() {
        profileLoaded = false;
    }

    public void loadProfile() {
        if (userRepository == null) return;

        // Use dev branch guest check
        Boolean guestNow = _isGuest.getValue();
        if (guestNow != null && guestNow) return;

        if (profileLoaded) return;
        profileLoaded = true;

        userRepository.getProfileAsync(user -> {
            if (user != null) {
                _name.postValue(user.getName() != null  ? user.getName()  : "");
                _email.postValue(user.getEmail() != null ? user.getEmail() : "");
                _phone.postValue(user.getPhone() != null ? user.getPhone() : "");
                String role = user.getRole() != null ? user.getRole() : "entrant";
                _role.postValue(role);
                if (_viewMode.getValue() == null || _viewMode.getValue().isEmpty()) {
                    _viewMode.postValue(role);
                }
                _notificationsEnabled.postValue(user.getNotificationsEnabled());
                _bio.postValue(user.getBio() != null ? user.getBio() : "");
                _profilePhotoUrl.postValue(user.getProfilePhotoUrl() != null ? user.getProfilePhotoUrl() : "");
                _organizationName.postValue(user.getOrganizationName() != null ? user.getOrganizationName() : "");

                boolean guest = user.isGuest() || (user.getLastLoginAt() == null || user.getLastLoginAt().isEmpty());
                _isGuest.postValue(guest);
            } else {
                _name.postValue("");
                _email.postValue("");
                _phone.postValue("");
                _isGuest.postValue(true);
            }
        });
    }

    public void saveProfile() {
        if (userRepository == null) return;

        String name  = _name.getValue()  != null ? _name.getValue().trim()  : "";
        String email = _email.getValue() != null ? _email.getValue().trim() : "";
        String phone = _phone.getValue() != null ? _phone.getValue().trim() : "";
        boolean notificationsEnabled = _notificationsEnabled.getValue() != null
                ? _notificationsEnabled.getValue() : true;

        _nameError.setValue(null);
        _emailError.setValue(null);

        if (name.isEmpty()) {
            _nameError.setValue("Name is required");
            return;
        }

        if (email.isEmpty()) {
            _emailError.setValue("Email is required");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _emailError.setValue("Enter a valid email address");
            return;
        }

        _isSaving.setValue(true);

        String bio = _bio.getValue() != null ? _bio.getValue().trim() : "";
        String orgName = _organizationName.getValue() != null ? _organizationName.getValue().trim() : "";

        Map<String, Object> data = new HashMap<>();
        data.put("name",  name);
        data.put("email", email);
        data.put("phone", phone);
        data.put("notificationsEnabled", notificationsEnabled);
        data.put("bio",   bio);
        if (!orgName.isEmpty()) {
            data.put("organizationName", orgName);
        }

        userRepository.saveProfileAsync(data, error -> {
            _isSaving.postValue(false);
            if (error == null) {
                profileLoaded = false;
                _toastMessage.postValue("Profile saved!");
            } else {
                _toastMessage.postValue("Error saving profile: " + error.getMessage());
            }
        });
    }

    public void deleteProfile() {
        if (userRepository == null) return;

        userRepository.deleteProfileAsync(error -> {
            if (error == null) {
                _name.postValue("");
                _email.postValue("");
                _phone.postValue("");
                _isGuest.postValue(true);
                _profileDeleted.postValue(true);
                _toastMessage.postValue("Profile deleted");
            } else {
                _toastMessage.postValue("Error deleting profile: " + error.getMessage());
            }
        });
    }

    public void logout() {
        if (userRepository != null) {
            userRepository.clearLocalSession();
        } else {
            FirebaseAuth.getInstance().signOut();
        }
        _name.setValue("");
        _email.setValue("");
        _phone.setValue("");
        _isGuest.setValue(true);
        _toastMessage.setValue("Logged out");
    }

    public void clearToast() {
        _toastMessage.setValue(null);
    }

    public void loadOrganizerStats(String userId, EventRepository eventRepository) {
        eventRepository.getEventsByOrganizer(userId)
                .addOnSuccessListener(querySnapshot -> {
                    _eventsCreated.postValue(querySnapshot.size());
                })
                .addOnFailureListener(e -> _eventsCreated.postValue(0));
    }

    // Dev branch stats method
    public void loadEntrantStats(String userId, RegistrationRepository registrationRepository) {
        registrationRepository.getEntrantStats(userId, (eventsJoined, eventsWon) -> {
            _eventsJoined.postValue(eventsJoined);
            _eventsWon.postValue(eventsWon);
        });
    }

    /**
     * YOUR CLOUDINARY UPLOAD LOGIC
     */
    public void uploadProfilePhoto(android.net.Uri photoUri, StorageRepository storageRepo) {
        if (userRepository == null || photoUri == null) return;

        userRepository.getCurrentUserIdAsync(uuid -> {
            if (uuid == null) return;

            // Use the Cloudinary callback method you implemented
            storageRepo.uploadImage(photoUri, new StorageRepository.CloudinaryCallback() {
                @Override
                public void onSuccess(String url) {
                    _profilePhotoUrl.postValue(url);
                    Map<String, Object> data = new HashMap<>();
                    data.put("profilePhotoUrl", url);
                    userRepository.saveProfileAsync(data, error -> {
                        if (error == null) {
                            _toastMessage.postValue("Photo updated!");
                        } else {
                            _toastMessage.postValue("Failed to save photo URL");
                        }
                    });
                }

                @Override
                public void onError(String errorMessage) {
                    _toastMessage.postValue("Photo upload failed: " + errorMessage);
                }
            });
        });
    }
}