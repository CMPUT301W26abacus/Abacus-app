package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PreferencesFragment
 *
 * Allows entrant users to set their event category preferences, preferred
 * location range, and event size preference. Preferences are persisted
 * to Firestore via UserRepository.
 */
public class PreferencesFragment extends Fragment {

    private PreferencesViewModel viewModel;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preferences, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        UserLocalDataSource  local  = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remote = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository       repo   = new UserRepository(local, remote);

        viewModel = new ViewModelProvider(this).get(PreferencesViewModel.class);
        viewModel.init(repo);

        // Back button
        view.<android.widget.ImageButton>findViewById(R.id.btnBack).setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        ChipGroup cgCategories    = view.findViewById(R.id.cgCategories);
        Slider    sliderLocation  = view.findViewById(R.id.sliderLocationRange);
        TextView  tvSliderValue   = view.findViewById(R.id.tvSliderValue);
        RadioGroup rgEventSize    = view.findViewById(R.id.rgEventSize);
        Button    btnSave         = view.findViewById(R.id.btnSavePrefs);

        // Slider value label
        sliderLocation.addOnChangeListener((slider, value, fromUser) ->
                tvSliderValue.setText((int) value + " km"));

        // Observe existing preferences and pre-populate UI
        viewModel.getPreferences().observe(getViewLifecycleOwner(), prefs -> {
            if (prefs == null || prefs.isEmpty()) return;

            // Restore category chips
            Object cats = prefs.get("categories");
            if (cats instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> categories = (List<String>) cats;
                for (int i = 0; i < cgCategories.getChildCount(); i++) {
                    View child = cgCategories.getChildAt(i);
                    if (child instanceof Chip) {
                        Chip chip = (Chip) child;
                        chip.setChecked(categories.contains(chip.getText().toString()));
                    }
                }
            }

            // Restore slider
            Object range = prefs.get("locationRangeKm");
            if (range instanceof Number) {
                sliderLocation.setValue(((Number) range).floatValue());
            }

            // Restore event size
            Object size = prefs.get("eventSize");
            if ("small".equals(size)) rgEventSize.check(R.id.rbSmall);
            else if ("medium".equals(size)) rgEventSize.check(R.id.rbMedium);
            else if ("large".equals(size)) rgEventSize.check(R.id.rbLarge);
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearToast();
            }
        });

        viewModel.getIsSaving().observe(getViewLifecycleOwner(), saving -> {
            btnSave.setEnabled(!saving);
            btnSave.setText(saving ? "Saving…" : "Save Preferences");
        });

        btnSave.setOnClickListener(v -> {
            // Collect selected categories
            List<String> selectedCategories = new ArrayList<>();
            for (int i = 0; i < cgCategories.getChildCount(); i++) {
                View child = cgCategories.getChildAt(i);
                if (child instanceof Chip && ((Chip) child).isChecked()) {
                    selectedCategories.add(((Chip) child).getText().toString());
                }
            }

            // Event size
            String eventSize = "";
            int sizeId = rgEventSize.getCheckedRadioButtonId();
            if (sizeId == R.id.rbSmall)       eventSize = "small";
            else if (sizeId == R.id.rbMedium) eventSize = "medium";
            else if (sizeId == R.id.rbLarge)  eventSize = "large";

            Map<String, Object> prefs = new HashMap<>();
            prefs.put("categories", selectedCategories);
            prefs.put("locationRangeKm", (int) sliderLocation.getValue());
            prefs.put("eventSize", eventSize);

            viewModel.savePreferences(prefs);
        });

        viewModel.loadPreferences();
    }
}
