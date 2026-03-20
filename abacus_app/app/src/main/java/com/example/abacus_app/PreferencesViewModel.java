package com.example.abacus_app;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.HashMap;
import java.util.Map;

/**
 * PreferencesViewModel
 *
 * Holds state for the entrant event preferences screen.
 * Delegates persistence to UserRepository.savePreferencesAsync.
 */
public class PreferencesViewModel extends ViewModel {

    private final MutableLiveData<Map<String, Object>> _preferences =
            new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Boolean> _isSaving   = new MutableLiveData<>(false);
    private final MutableLiveData<String>  _toastMessage = new MutableLiveData<>(null);

    public LiveData<Map<String, Object>> getPreferences() { return _preferences; }
    public LiveData<Boolean> getIsSaving()                { return _isSaving; }
    public LiveData<String>  getToastMessage()            { return _toastMessage; }

    private UserRepository userRepository;

    public void init(UserRepository repo) {
        this.userRepository = repo;
    }

    public void loadPreferences() {
        if (userRepository == null) return;
        userRepository.getProfileAsync(user -> {
            if (user != null && user.getPreferences() != null) {
                _preferences.postValue(user.getPreferences());
            } else {
                _preferences.postValue(new HashMap<>());
            }
        });
    }

    public void savePreferences(Map<String, Object> prefs) {
        if (userRepository == null) return;
        _isSaving.setValue(true);
        userRepository.savePreferencesAsync(prefs, error -> {
            _isSaving.postValue(false);
            if (error == null) {
                _preferences.postValue(prefs);
                _toastMessage.postValue("Preferences saved!");
            } else {
                _toastMessage.postValue("Error saving preferences: " + error.getMessage());
            }
        });
    }

    public void clearToast() {
        _toastMessage.setValue(null);
    }
}
