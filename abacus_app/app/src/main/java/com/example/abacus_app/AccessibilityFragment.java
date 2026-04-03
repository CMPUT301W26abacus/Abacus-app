package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * AccessibilityFragment
 *
 * Accessibility settings:
 * - Large text: Quick toggle for text scaling
 * - High contrast: Switch for increased contrast mode
 * - Text size: Slider with live preview + Apply button (triggers recreate, returns here)
 * - One-handed mode: Switch for optimized single-hand navigation
 * - Reduce motion: Switch to disable animations
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

        if (helper.isHighContrast()) {
            AccessibilityHelper.applyHighContrast(view);
        }

        view.<android.widget.ImageButton>findViewById(R.id.btnBack).setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());

        // ── Quick toggles (largeText boolean key) ──
        android.content.SharedPreferences quickPrefs =
                requireContext().getSharedPreferences("accessibility_prefs",
                        android.content.Context.MODE_PRIVATE);

        SwitchMaterial switchLargeText = view.findViewById(R.id.switchLargeText);
        LinearLayout llTextSizeSection = view.findViewById(R.id.llTextSizeSection);
        Slider slider = view.findViewById(R.id.sliderTextScale);

        switchLargeText.setChecked(quickPrefs.getBoolean("largeText", false));
        updateTextSizeSectionVisibility(switchLargeText.isChecked(), llTextSizeSection, slider);

        switchLargeText.setOnCheckedChangeListener((btn, isChecked) -> {
            quickPrefs.edit().putBoolean("largeText", isChecked).apply();
            updateTextSizeSectionVisibility(isChecked, llTextSizeSection, slider);
        });

        // ── High contrast ──
        SwitchMaterial switchHighContrast = view.findViewById(R.id.switchHighContrast);
        switchHighContrast.setChecked(helper.isHighContrast());
        switchHighContrast.setOnCheckedChangeListener((btn, isChecked) -> {
            helper.setHighContrast(isChecked);
            // Apply to this fragment's view immediately — other screens pick it up on next creation
            if (isChecked) {
                AccessibilityHelper.applyHighContrast(view);
            }
        });

        // ── Text size slider ──
        TextView tvScaleValue = view.findViewById(R.id.tvTextScaleValue);
        TextView tvPreview    = view.findViewById(R.id.tvTextPreview);
        Button   btnApply     = view.findViewById(R.id.btnApplyTextScale);

        slider.setValue(clampScale(helper.getTextScale(), switchLargeText.isChecked()));
        updateTextScaleUI(slider.getValue(), tvScaleValue, tvPreview);

        slider.addOnChangeListener((s, value, fromUser) ->
                updateTextScaleUI(value, tvScaleValue, tvPreview));

        btnApply.setOnClickListener(v -> {
            helper.setTextScale(slider.getValue());
            // Flag MainActivity to navigate back here after recreate
            helper.setReturnToAccessibility(true);
            requireActivity().recreate();
        });

        // ── One-handed mode ──
        SwitchMaterial switchOneHanded = view.findViewById(R.id.switchOneHanded);
        switchOneHanded.setChecked(helper.isOneHandedMode());
        switchOneHanded.setOnCheckedChangeListener((btn, isChecked) ->
                helper.setOneHandedMode(isChecked));

        // ── Reduce motion ──
        SwitchMaterial switchReduceMotion = view.findViewById(R.id.switchReduceMotion);
        switchReduceMotion.setChecked(helper.isReduceMotion());
        switchReduceMotion.setOnCheckedChangeListener((btn, isChecked) ->
                helper.setReduceMotion(isChecked));
    }

    private void updateTextSizeSectionVisibility(boolean isLargeTextEnabled, LinearLayout llTextSizeSection, Slider slider) {
        llTextSizeSection.setVisibility(isLargeTextEnabled ? android.view.View.VISIBLE : android.view.View.GONE);
        // Update slider's max value based on whether large text is enabled
        if (isLargeTextEnabled) {
            slider.setValueTo(1.9f);
        } else {
            // Clamp current value if it exceeds the new max
            if (slider.getValue() > 1.6f) {
                slider.setValue(1.6f);
            }
            slider.setValueTo(1.6f);
        }
    }

    private float clampScale(float scale, boolean isLargeTextEnabled) {
        float maxScale = isLargeTextEnabled ? 1.9f : 1.6f;
        return Math.max(0.8f, Math.min(maxScale, scale));
    }

    private void updateTextScaleUI(float scale, TextView tvScaleValue, TextView tvPreview) {
        int percent = Math.round(scale * 100);
        tvScaleValue.setText(percent + "%");
        tvPreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16 * scale);
    }
}
