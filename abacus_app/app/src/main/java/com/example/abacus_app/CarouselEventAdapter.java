package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CarouselEventAdapter
 *
 * Horizontal carousel adapter for featured events on the home screen.
 * Uses item_carousel_event.xml cards (280dp × 160dp).
 */
public class CarouselEventAdapter extends RecyclerView.Adapter<CarouselEventAdapter.ViewHolder> {

    public interface OnCarouselEventClickListener {
        void onCarouselEventClick(Event event);
    }

    private List<Event> events = new ArrayList<>();
    private final OnCarouselEventClickListener clickListener;

    public CarouselEventAdapter(List<Event> events, OnCarouselEventClickListener clickListener) {
        this.events = events != null ? events : new ArrayList<>();
        this.clickListener = clickListener;
    }

    public void setEvents(List<Event> newEvents) {
        this.events = newEvents != null ? newEvents : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_carousel_event, parent, false);
        return new ViewHolder(view, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= 0 && position < events.size()) {
            holder.bind(events.get(position));
        }
    }

    @Override
    public int getItemCount() { return events.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivPoster;
        private final TextView tvTitle;
        private final Chip chipDate;
        private final OnCarouselEventClickListener clickListener;

        ViewHolder(@NonNull View itemView, OnCarouselEventClickListener clickListener) {
            super(itemView);
            this.clickListener = clickListener;
            ivPoster  = itemView.findViewById(R.id.iv_carousel_poster);
            tvTitle   = itemView.findViewById(R.id.tv_carousel_title);
            chipDate  = itemView.findViewById(R.id.chip_carousel_date);
        }

        void bind(Event event) {
            tvTitle.setText(event.getTitle() != null ? event.getTitle() : "");

            // Format registration end date
            if (event.getRegistrationEnd() != null) {
                java.util.Date date = event.getRegistrationEnd().toDate();
                chipDate.setText(new SimpleDateFormat("MMM d", Locale.getDefault()).format(date));
            } else {
                chipDate.setText("");
            }

            // Load poster with Glide
            String posterUrl = event.getPosterImageUrl();
            if (posterUrl != null && !posterUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(posterUrl)
                        .placeholder(R.drawable.ic_event_poster)
                        .centerCrop()
                        .into(ivPoster);
            } else {
                ivPoster.setImageResource(R.drawable.ic_event_poster);
            }

            // Navigate on tap
            itemView.setOnClickListener(v -> {
                if (clickListener != null && event.getEventId() != null) {
                    clickListener.onCarouselEventClick(event);
                }
            });
        }
    }
}
