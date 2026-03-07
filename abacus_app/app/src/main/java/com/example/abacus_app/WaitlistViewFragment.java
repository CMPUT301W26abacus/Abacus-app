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

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Fragment for organizers to view the waitlist of a specific event.
 * Displays total count and list of entrants.
 * 
 * @author Himesh
 * @version 1.0
 */
public class WaitlistViewFragment extends Fragment {

    private String eventId;
    private String eventTitle;
    private RecyclerView recyclerView;
    private WaitlistAdapter adapter;
    private List<WaitlistEntry> entries = new ArrayList<>();
    private TextView tvEventName, tvCount;
    private final RegistrationRemoteDataSource dataSource = new RegistrationRemoteDataSource();

    public static WaitlistViewFragment newInstance(String eventId, String eventTitle) {
        WaitlistViewFragment fragment = new WaitlistViewFragment();
        Bundle args = new Bundle();
        args.putString("EVENT_ID", eventId);
        args.putString("EVENT_TITLE", eventTitle);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString("EVENT_ID");
            eventTitle = getArguments().getString("EVENT_TITLE");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.waitlist_view_fragment, container, false);

        tvEventName = view.findViewById(R.id.tv_event_name);
        tvCount = view.findViewById(R.id.tv_waitlist_count);
        recyclerView = view.findViewById(R.id.rv_waitlist);

        tvEventName.setText(eventTitle);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(adapter);

        loadWaitlist();

        return view;
    }

    private void loadWaitlist() {
        if (eventId == null) return;

        dataSource.getWaitlist(eventId).addOnSuccessListener(queryDocumentSnapshots -> {
            entries.clear();
            for (DocumentSnapshot doc : queryDocumentSnapshots) {
                // Manually mapping from Firestore for safety
                String userId = doc.getString("userId");
                String status = doc.getString("status");
                // WaitlistEntry constructor or factory method should handle this
                // For simplicity assuming standard POJO mapping works if fields match
                WaitlistEntry entry = doc.toObject(WaitlistEntry.class);
                if (entry != null) entries.add(entry);
            }
            tvCount.setText("Total Entrants: " + entries.size());
            adapter.notifyDataSetChanged();
        }).addOnFailureListener(e -> {
            Toast.makeText(getContext(), "Failed to load waitlist", Toast.LENGTH_SHORT).show();
        });
    }
}
