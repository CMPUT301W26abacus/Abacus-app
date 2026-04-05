package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.card.MaterialCardView;

/**
 * OrganizerToolsFragment
 *
 * Landing page for organizer/admin tools, presented as tabs.
 * Features a "Create Event" button in the header and tabs for "Manage Events" and "Browse Entrants".
 */
public class OrganizerToolsFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.organizer_tools_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Create Event card button
        MaterialCardView btnCreateEvent = view.findViewById(R.id.btn_create_event);
        btnCreateEvent.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showFragment(R.id.organizerCreateFragment, false);
            }
        });

        // Manage Events card
        MaterialCardView cardManageEvents = view.findViewById(R.id.card_manage_events);
        cardManageEvents.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).showFragment(R.id.organizerManageFragment, false);
        });

        // Browse Entrants card - Redirects to OrganizerLogsFragment which handles profile browsing and invites
        MaterialCardView cardBrowseEntrants = view.findViewById(R.id.card_browse_entrants);
        cardBrowseEntrants.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).showFragment(R.id.organizerLogsFragment, false);
        });
    }
}
