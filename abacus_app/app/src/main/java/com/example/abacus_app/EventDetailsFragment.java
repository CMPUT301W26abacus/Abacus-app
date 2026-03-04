/**
 * EventDetailsFragment.java
 *
 * Role: Displays the full details of a selected event. Accessible by tapping
 * any event card from the main browse list. Provides a button to view the
 * event's QR code via EventQrFragment. Bottom navigation is hidden on this
 * screen for a focused full-screen experience.
 *
 * Outstanding issues:
 * - Event data is not yet populated; fields will be bound from a Firestore
 *   document once Firebase integration is complete.
 * - Event ID and name passed to EventQrFragment are hardcoded test values.
 */
package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class EventDetailsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.event_details_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Back button → returns to home screen
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (!Navigation.findNavController(view).popBackStack()) {
                // Nothing left to pop — go back to home
                ((MainActivity) requireActivity()).showHome();
            }
        });

        // QR button → EventQrFragment, passing event ID and name
        // Replace hardcoded values with real Firestore event data later
        ImageButton btnViewQr = view.findViewById(R.id.btn_view_qr);
        btnViewQr.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putString(EventQrFragment.ARG_EVENT_ID, "event_test_12345");
            args.putString(EventQrFragment.ARG_EVENT_NAME, "Test Event");
            Navigation.findNavController(view).navigate(R.id.eventQrFragment, args);
        });
    }
}