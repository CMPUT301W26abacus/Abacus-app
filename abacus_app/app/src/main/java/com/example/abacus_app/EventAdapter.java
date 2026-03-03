package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(String eventTitle);
    }

    private final List<String> eventTitles;
    private final OnEventClickListener listener;

    public EventAdapter(List<String> eventTitles, OnEventClickListener listener) {
        this.eventTitles = eventTitles;
        this.listener = listener;
    }

    // No-click constructor for backwards compatibility
    public EventAdapter(List<String> eventTitles) {
        this.eventTitles = eventTitles;
        this.listener = null;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_list, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        String title = eventTitles.get(position);
        holder.tvTitle.setText(title);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onEventClick(title);
        });
    }

    @Override
    public int getItemCount() {
        return eventTitles.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_event_title);
        }
    }
}