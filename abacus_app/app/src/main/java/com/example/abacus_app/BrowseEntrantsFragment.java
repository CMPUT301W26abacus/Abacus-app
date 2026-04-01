package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * BrowseEntrantsFragment
 *
 * Allows organizers to browse entrants for their events.
 * Shows a list of events and their entrants.
 */
public class BrowseEntrantsFragment extends Fragment {

    private RecyclerView recyclerView;
    private EventAdapter adapter;
    private List<Event> events;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.browse_entrants_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        events = new ArrayList<>();
        recyclerView = view.findViewById(R.id.rv_events);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new EventAdapter(events, new EventAdapter.OnEventClickListener() {
            @Override
            public void onEventClick(String eventTitle, boolean autoJoin) {
                // When an event is clicked, show its entrants
                // Find the event by title to get its ID
                for (Event event : events) {
                    if (event.getTitle().equals(eventTitle)) {
                        ManageEventFragment manageFragment = ManageEventFragment.newInstance(
                                event.getEventId(),
                                event.getTitle()
                        );
                        // Show the fragment using a fragment transaction
                        if (getActivity() instanceof MainActivity) {
                            getParentFragmentManager().beginTransaction()
                                    .replace(R.id.nav_host_fragment, manageFragment)
                                    .addToBackStack(null)
                                    .commit();
                        }
                        break;
                    }
                }
            }
        });
        recyclerView.setAdapter(adapter);
        com.google.android.material.textfield.TextInputEditText searchBar = view.findViewById(R.id.search_bar);
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                // filter logic here when you implement loadEvents()
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        searchBar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(searchBar.getWindowToken(), 0);
                searchBar.clearFocus();
                return true;
            }
            return false;
        });
        // Load events
        loadEvents();

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (getActivity() instanceof MainActivity)
                            ((MainActivity) getActivity()).showFragment(R.id.organizerToolsFragment, true);
                    }
                });
    }

    private void loadEvents() {
        // TODO: Load events from repository/viewmodel
        // For now, show empty list
    }
}
