package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * AccessibilityFragment
 *
 * Lets users toggle color-blind mode and large text.
 * Reads/writes settings directly via {@link AccessibilityHelper} (no ViewModel needed).
 * Calls {@code requireActivity().recreate()} after each toggle so the change takes effect
 * immediately without requiring a full app restart.
 */
public class AccessibilityFragment extends Fragment {

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_accessibility, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        AccessibilityHelper helper = new AccessibilityHelper(requireContext());

        view.<android.widget.ImageButton>findViewById(R.id.btnBack).setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        SwitchMaterial switchColorBlind = view.findViewById(R.id.switchColorBlind);
        SwitchMaterial switchLargeText  = view.findViewById(R.id.switchLargeText);

        // Load persisted state without triggering listeners
        switchColorBlind.setChecked(helper.isColorBlindMode());
        switchLargeText.setChecked(helper.isLargeText());

        switchColorBlind.setOnCheckedChangeListener((btn, isChecked) -> {
            helper.setColorBlindMode(isChecked);
            requireActivity().recreate();
        });

        switchLargeText.setOnCheckedChangeListener((btn, isChecked) -> {
            helper.setLargeText(isChecked);
            requireActivity().recreate();
        });
    }
}
