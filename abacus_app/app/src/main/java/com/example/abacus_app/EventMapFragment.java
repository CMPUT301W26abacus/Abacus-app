package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment that displays a map with markers for all entrants who joined the waitlist.
 * Only shown if the event has geolocation enabled.
 */
public class EventMapFragment extends Fragment implements OnMapReadyCallback {

    public static final String ARG_EVENT_ID = "eventId";
    public static final String ARG_EVENT_NAME = "eventName";

    private String eventId;
    private String eventName;
    private GoogleMap mMap;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        eventId = getArguments() != null ? getArguments().getString(ARG_EVENT_ID) : null;
        eventName = getArguments() != null ? getArguments().getString(ARG_EVENT_NAME, "Event Map") : "Event Map";

        TextView tvTitle = view.findViewById(R.id.tv_map_title);
        tvTitle.setText(eventName);

        ImageButton btnBack = view.findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> Navigation.findNavController(view).popBackStack());

        db = FirebaseFirestore.getInstance();

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        loadEntrantLocations();
    }

    private void loadEntrantLocations() {
        if (eventId == null) return;

        db.collection("events").document(eventId).collection("waitlist")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<LatLng> locations = new ArrayList<>();
                    int totalEntrants = queryDocumentSnapshots.size();
                    
                    for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                        Double lat = doc.getDouble("latitude");
                        Double lng = doc.getDouble("longitude");
                        String name = doc.getString("userName");
                        if (name == null) name = doc.getString("guestName");
                        if (name == null) name = "Anonymous Entrant";
                        String status = doc.getString("status");

                        if (lat != null && lng != null) {
                            LatLng pos = new LatLng(lat, lng);
                            locations.add(pos);
                            mMap.addMarker(new MarkerOptions()
                                    .position(pos)
                                    .title(name)
                                    .snippet("Status: " + (status != null ? status : "unknown")));
                        }
                    }

                    if (!locations.isEmpty()) {
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        for (LatLng latLng : locations) {
                            builder.include(latLng);
                        }
                        LatLngBounds bounds = builder.build();

                        // If only one pin or all pins at same location, use center zoom
                        if (locations.size() == 1 || bounds.northeast.equals(bounds.southwest)) {
                            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(locations.get(0), 12));
                        } else {
                            int padding = 200; 
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                        }
                        
                        Toast.makeText(getContext(), "Showing " + locations.size() + " of " + totalEntrants + " entrant locations.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), "No entrant locations found.", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load locations.", Toast.LENGTH_SHORT).show();
                });
    }
}