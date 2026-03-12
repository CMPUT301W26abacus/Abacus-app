package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * OrganizerToolsFragment
 *
 * Landing page for organizer/admin tools.
 * Organizers see: Create Event, My Events.
 * Admins see additional: Browse Users, Browse Images.
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

        Button btnCreateEvent = view.findViewById(R.id.btn_create_event);
        Button btnMyEvents    = view.findViewById(R.id.btn_my_events);

        btnCreateEvent.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showFragment(R.id.organizerCreateFragment, false));

        btnMyEvents.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showFragment(R.id.organizerManageFragment, false));
    }
}