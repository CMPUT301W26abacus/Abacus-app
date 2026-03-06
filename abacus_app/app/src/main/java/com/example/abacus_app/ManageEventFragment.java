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
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * UI Controller for the active event management screen.
 * Displays lists of waiting, chosen, cancelled, and enrolled entrants.
 * Owner: Himesh
 */
public class ManageEventFragment extends Fragment {

    private String eventId;
    private String eventTitle;
    private ManageEventViewModel viewModel;
    private RecyclerView recyclerView;
    private WaitlistAdapter adapter;
    private List<WaitlistEntry> entries = new ArrayList<>();
    private TextView tvEventName, tvCount;

    public static ManageEventFragment newInstance(String eventId, String eventTitle) {
        ManageEventFragment fragment = new ManageEventFragment();
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
        View view = inflater.inflate(R.layout.fragment_manage_event, container, false);

        viewModel = new ViewModelProvider(this).get(ManageEventViewModel.class);
        
        tvEventName = view.findViewById(R.id.tv_event_name);
        tvCount = view.findViewById(R.id.tv_waitlist_count);
        recyclerView = view.findViewById(R.id.rv_waitlist);

        tvEventName.setText(eventTitle);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new WaitlistAdapter(entries);
        recyclerView.setAdapter(adapter);

        observeViewModel();
        
        if (eventId != null) {
            viewModel.loadWaitlist(eventId);
        }

        return view;
    }

    private void observeViewModel() {
        viewModel.getEntrants().observe(getViewLifecycleOwner(), newEntries -> {
            if (newEntries != null) {
                entries.clear();
                entries.addAll(newEntries);
                tvCount.setText("Total Entrants: " + entries.size());
                adapter.notifyDataSetChanged();
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), err -> {
            if (err != null) Toast.makeText(getContext(), err, Toast.LENGTH_SHORT).show();
        });
    }
}
