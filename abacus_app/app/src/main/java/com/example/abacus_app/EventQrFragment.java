/**
 * EventQrFragment.java
 *
 * Role: Displays the QR code for a specific event. The QR code encodes the
 * event's unique ID, which can be scanned by other users via the QR scanner
 * (MainQrScanFragment) to access the event details page directly.
 *
 * Design pattern: Accepts event ID and event name as fragment arguments so it
 * can be reused for any event. Uses QRCodeGenerator to produce the bitmap.
 *
 * Outstanding issues:
 * - Event ID and name are hardcoded as fallback test values; replace with
 *   real Firestore document ID and event name once Firebase is integrated.
 */
package com.example.abacus_app;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

public class EventQrFragment extends Fragment {

    /** Argument key for the event's unique ID to encode in the QR code. */
    public static final String ARG_EVENT_ID = "event_id";

    /** Argument key for the event's display name shown above the QR code. */
    public static final String ARG_EVENT_NAME = "event_name";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.event_qr_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Hardcoded fallback — replace with real Firestore event data later
        String eventId = "event_test_12345";
        String eventName = "Test Event";

        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID, "event_test_12345");
            eventName = getArguments().getString(ARG_EVENT_NAME, "Test Event");
        }

        // Set event name and ID text
        TextView tvEventName = view.findViewById(R.id.tv_event_name);
        TextView tvEventId = view.findViewById(R.id.tv_event_id);
        tvEventName.setText(eventName);
        tvEventId.setText(eventId);

        // Generate and display QR code
        ImageView ivQrCode = view.findViewById(R.id.iv_qr_code);
        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(eventId, 512);
        if (qrBitmap != null) {
            ivQrCode.setImageBitmap(qrBitmap);
        }

        // Back button
        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v ->
                Navigation.findNavController(view).navigateUp());
    }
}