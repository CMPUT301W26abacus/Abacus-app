package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
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
 * Color-blind type: single-select via manual mutual exclusion (RadioButtons inside
 * clickable rows — RadioGroup cannot manage non-direct children).
 * Text size: Slider with live preview + Apply button (triggers recreate, returns here).
 * Increase contrast / Reduce motion: Switch, no recreate needed.
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

        // ── Quick toggles (colorBlindMode + largeText boolean keys) ──
        android.content.SharedPreferences quickPrefs =
                requireContext().getSharedPreferences("accessibility_prefs",
                        android.content.Context.MODE_PRIVATE);

        SwitchMaterial switchColorBlind = view.findViewById(R.id.switchColorBlind);
        switchColorBlind.setChecked(quickPrefs.getBoolean("colorBlindMode", false));
        switchColorBlind.setOnCheckedChangeListener((btn, isChecked) ->
                quickPrefs.edit().putBoolean("colorBlindMode", isChecked).apply());

        SwitchMaterial switchLargeText = view.findViewById(R.id.switchLargeText);
        switchLargeText.setChecked(quickPrefs.getBoolean("largeText", false));
        switchLargeText.setOnCheckedChangeListener((btn, isChecked) ->
                quickPrefs.edit().putBoolean("largeText", isChecked).apply());

        // ── Color-blind type — manual single selection ──
        RadioButton rbNone    = view.findViewById(R.id.rbColorBlindNone);
        RadioButton rbProtan  = view.findViewById(R.id.rbProtanopia);
        RadioButton rbDeutan  = view.findViewById(R.id.rbDeuteranopia);
        RadioButton rbTritan  = view.findViewById(R.id.rbTritanopia);
        RadioButton rbAchrom  = view.findViewById(R.id.rbAchromatopsia);

        RadioButton[] allRbs  = {rbNone, rbProtan, rbDeutan, rbTritan, rbAchrom};
        String[] allTypes     = {
            AccessibilityHelper.COLOR_BLIND_NONE,
            AccessibilityHelper.COLOR_BLIND_PROTANOPIA,
            AccessibilityHelper.COLOR_BLIND_DEUTERANOPIA,
            AccessibilityHelper.COLOR_BLIND_TRITANOPIA,
            AccessibilityHelper.COLOR_BLIND_ACHROMATOPSIA
        };
        int[] rowIds = {
            R.id.rowColorBlindNone, R.id.rowProtanopia,
            R.id.rowDeuteranopia,   R.id.rowTritanopia, R.id.rowAchromatopsia
        };

        // Restore saved selection
        String savedType = helper.getColorBlindType();
        for (int i = 0; i < allTypes.length; i++) {
            allRbs[i].setChecked(allTypes[i].equals(savedType));
        }

        // Wire row clicks — check selected, uncheck all others, save, apply locally
        for (int i = 0; i < rowIds.length; i++) {
            final int idx = i;
            view.findViewById(rowIds[i]).setOnClickListener(v -> {
                for (RadioButton rb : allRbs) rb.setChecked(false);
                allRbs[idx].setChecked(true);
                helper.setColorBlindType(allTypes[idx]);
                // Color-blind palette applies in adapters/fragments when they (re)bind;
                // no full recreate needed — the user sees it when they navigate back.
            });
        }

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
        Slider slider        = view.findViewById(R.id.sliderTextScale);
        TextView tvScaleValue = view.findViewById(R.id.tvTextScaleValue);
        TextView tvPreview    = view.findViewById(R.id.tvTextPreview);
        Button   btnApply     = view.findViewById(R.id.btnApplyTextScale);

        slider.setValue(clampScale(helper.getTextScale()));
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

    private float clampScale(float scale) {
        return Math.max(0.8f, Math.min(1.6f, scale));
    }

    private void updateTextScaleUI(float scale, TextView tvScaleValue, TextView tvPreview) {
        int percent = Math.round(scale * 100);
        tvScaleValue.setText(percent + "%");
        tvPreview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16 * scale);
    }
}
