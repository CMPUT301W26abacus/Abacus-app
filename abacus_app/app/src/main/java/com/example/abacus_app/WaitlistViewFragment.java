package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for organizers to view the waitlist of a specific event.
 * Displays the total entrant count and a scrollable list of all waitlist entries
 * regardless of their current status (waitlisted, invited, accepted, etc.).
 *
 * <p>Launched from the organizer management screen with {@code EVENT_ID} and
 * {@code EVENT_TITLE} arguments. Fetches all entries via {@link RegistrationRepository}
 * and binds them to a {@link WaitlistAdapter}.
 *
 * @author Himesh
 * @version 1.0
 */
public class WaitlistViewFragment extends Fragment {

    private String eventId;
    private String eventTitle;
    private RecyclerView recyclerView;
    private WaitlistAdapter adapter;
    private final List<WaitlistEntry> entries = new ArrayList<>();
    private TextView tvEventName, tvCount;
    private final RegistrationRepository registrationRepository = new RegistrationRepository();

    /**
     * Creates a new instance of WaitlistViewFragment with the required arguments.
     *
     * @param eventId    the Firestore document ID of the event whose waitlist to display
     * @param eventTitle the display title of the event, shown in the fragment header
     * @return a new WaitlistViewFragment with arguments set
     */
    public static WaitlistViewFragment newInstance(String eventId, String eventTitle) {
        WaitlistViewFragment fragment = new WaitlistViewFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID", eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Reads the {@code EVENT_ID} and {@code EVENT_TITLE} arguments from the fragment's Bundle.
     *
     * @param savedInstanceState the previously saved instance state, or null
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId    = getArguments().getString("EVENT_ID");
            eventTitle = getArguments().getString("EVENT_TITLE");
        }
    }

    /**
     * Inflates the waitlist view layout, initialises the RecyclerView and header TextViews,
     * and triggers the initial waitlist load from Firestore.
     *
     * @param inflater           the LayoutInflater used to inflate the layout
     * @param container          the parent ViewGroup, or null if there is no parent
     * @param savedInstanceState the previously saved instance state, or null
     * @return the inflated root view for this fragment
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.waitlist_view_fragment, container, false);

        tvEventName  = view.findViewById(R.id.tv_event_name);
        tvCount      = view.findViewById(R.id.tv_waitlist_count);
        recyclerView = view.findViewById(R.id.rv_waitlist);

        tvEventName.setText(eventTitle);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(adapter);

        loadWaitlist();

        return view;
    }

    /**
     * Fetches all waitlist entries for the current event from Firestore via
     * {@link RegistrationRepository#getAllEntries} and updates the RecyclerView.
     * Displays a toast if the fetch fails. Updates the total entrant count label
     * regardless of success or failure.
     */
    private void loadWaitlist() {
        if (eventId == null) return;

        registrationRepository.getAllEntries(eventId, waitlist -> {
            entries.clear();
            if (waitlist != null) {
                entries.addAll(waitlist);
            } else {
                Toast.makeText(getContext(), "Failed to load waitlist", Toast.LENGTH_SHORT).show();
            }
            tvCount.setText("Total Entrants: " + entries.size());
            adapter.notifyDataSetChanged();
        });
    }
}