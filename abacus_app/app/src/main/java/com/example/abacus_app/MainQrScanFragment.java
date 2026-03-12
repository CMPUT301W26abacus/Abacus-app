/**
 * MainQrScanFragment.java
 *
 * Role: Provides a live camera QR code scanner. When a valid event QR code
 * is scanned, the encoded event ID is extracted and the user is navigated
 * to the corresponding event details page.
 *
 * Design pattern: Uses ZXing's DecoratedBarcodeView for camera handling and
 * decoding. The fragment pauses/resumes the scanner with the fragment lifecycle
 * to avoid camera resource leaks.
 */
package com.example.abacus_app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.util.List;

public class MainQrScanFragment extends Fragment {

    private DecoratedBarcodeView barcodeScanner;
    private boolean scanned = false; // prevent multiple navigations from one scan

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startScanning();
                } else {
                    Toast.makeText(requireContext(),
                            "Camera permission is required to scan QR codes.",
                            Toast.LENGTH_LONG).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.main_qr_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        barcodeScanner = view.findViewById(R.id.barcode_scanner);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> ((MainActivity) requireActivity()).showHome());

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScanning();
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    /**
     * Starts continuous QR code scanning. On a successful scan, extracts the
     * event ID from the result and navigates to EventDetailsFragment.
     * Ignores subsequent scans after the first to prevent double navigation.
     */
    private void startScanning() {
        scanned = false;
        barcodeScanner.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (scanned || result.getText() == null) return;
                scanned = true;

                String scannedEventId = result.getText();

                // Pass event ID to EventDetailsFragment
                // TODO: fetch real event title from Firestore using scannedEventId
                Bundle args = new Bundle();
                args.putString(EventDetailsFragment.ARG_EVENT_ID, scannedEventId);
                args.putString(EventDetailsFragment.ARG_EVENT_TITLE, "Scanned Event");

                // showFragment makes NavHost visible and navigates in one call
                ((MainActivity) requireActivity())
                        .showFragment(R.id.eventDetailsFragment, false, args);
            }

            @Override
            public void possibleResultPoints(List<com.google.zxing.ResultPoint> resultPoints) {
                // Optional: highlight finder points on screen
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (barcodeScanner != null) barcodeScanner.resume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (barcodeScanner != null) barcodeScanner.pause();
    }
}