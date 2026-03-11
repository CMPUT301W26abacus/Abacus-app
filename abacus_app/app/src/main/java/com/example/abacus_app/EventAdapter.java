package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(String eventTitle);
    }

    public interface OnEventDeleteListener {
        void onEventDelete(Event event);
    }

    private final List<Event> events;
    private final OnEventClickListener clickListener;
    private final OnEventDeleteListener deleteListener;
    private final boolean isAdmin;

    /**
     * Full constructor — used by MainActivity.
     * Pass a non-null deleteListener and isAdmin=true only for admin users.
     */
    public EventAdapter(List<Event> events,
                        OnEventClickListener clickListener,
                        OnEventDeleteListener deleteListener,
                        boolean isAdmin) {
        this.events         = events;
        this.clickListener  = clickListener;
        this.deleteListener = deleteListener;
        this.isAdmin        = isAdmin;
    }

    /**
     * Non-admin convenience constructor — delete button stays hidden.
     */
    public EventAdapter(List<Event> events, OnEventClickListener clickListener) {
        this(events, clickListener, null, false);
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);

        holder.tvTitle.setText(event.getTitle() != null ? event.getTitle() : "");

        // Load poster image with Glide — falls back to placeholder if no URL
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_event_poster)
                    .error(R.drawable.ic_event_poster)
                    .centerCrop()
                    .into(holder.ivPoster);
        } else {
            holder.ivPoster.setImageResource(R.drawable.ic_event_poster);
        }

        if (isAdmin && deleteListener != null) {
            // Admin mode: show delete button, hide join + favourite
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnJoinStatus.setVisibility(View.GONE);
            holder.btnFavourite.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(v -> deleteListener.onEventDelete(event));
        } else {
            // Normal mode: hide delete, show join + favourite
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnJoinStatus.setVisibility(View.VISIBLE);
            holder.btnFavourite.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onEventClick(event.getTitle());
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView ivPoster;
        TextView tvTitle;
        MaterialButton btnJoinStatus;
        MaterialButton btnDelete;
        ImageButton btnFavourite;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster      = itemView.findViewById(R.id.iv_event_poster);
            tvTitle       = itemView.findViewById(R.id.tv_event_title);
            btnJoinStatus = itemView.findViewById(R.id.btn_join_status);
            btnDelete     = itemView.findViewById(R.id.btn_admin_delete);
            btnFavourite  = itemView.findViewById(R.id.imageButton2);
        }
    }
}