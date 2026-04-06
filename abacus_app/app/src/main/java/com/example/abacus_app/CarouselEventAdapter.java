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
 * Displays up to 5 soonest upcoming events in item_carousel_event.xml cards (280dp × 160dp).
 * Tapping a card navigates to EventDetailsFragment for the selected event.
 */
public class CarouselEventAdapter extends RecyclerView.Adapter<CarouselEventAdapter.ViewHolder> {

    /**
     * Callback for carousel card tap events.
     */
    public interface OnCarouselEventClickListener {
        /**
         * Called when the user taps a carousel event card.
         *
         * @param event the event that was tapped
         */
        void onCarouselEventClick(Event event);
    }

    private List<Event> events = new ArrayList<>();
    private final OnCarouselEventClickListener clickListener;

    /**
     * Constructs a new CarouselEventAdapter.
     *
     * @param events        the initial list of events to display; null is treated as empty
     * @param clickListener callback invoked when a carousel card is tapped
     */
    public CarouselEventAdapter(List<Event> events, OnCarouselEventClickListener clickListener) {
        this.events = events != null ? events : new ArrayList<>();
        this.clickListener = clickListener;
    }

    /**
     * Replaces the current event list and refreshes the carousel.
     * Called by MainActivity's applyFilters() whenever the filtered event list changes.
     *
     * @param newEvents the new list of events to display; null is treated as empty
     */
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

    /**
     * Binds the event at the given position to the ViewHolder.
     * Bounds-checks the position before delegating to {@link ViewHolder#bind(Event)}.
     *
     * @param holder   the ViewHolder to bind into
     * @param position the position in the events list
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position >= 0 && position < events.size()) {
            holder.bind(events.get(position));
        }
    }

    @Override
    public int getItemCount() { return events.size(); }

    /**
     * ViewHolder for a single carousel event card.
     * Displays the event poster image, title, and registration end date chip.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView ivPoster;
        private final TextView tvTitle;
        private final Chip chipDate;
        private final OnCarouselEventClickListener clickListener;

        /**
         * Constructs a ViewHolder and looks up all child views.
         *
         * @param itemView      the inflated item_carousel_event view
         * @param clickListener callback to invoke when the card is tapped
         */
        ViewHolder(@NonNull View itemView, OnCarouselEventClickListener clickListener) {
            super(itemView);
            this.clickListener = clickListener;
            ivPoster  = itemView.findViewById(R.id.iv_carousel_poster);
            tvTitle   = itemView.findViewById(R.id.tv_carousel_title);
            chipDate  = itemView.findViewById(R.id.chip_carousel_date);
        }

        /**
         * Populates the card with the given event's data.
         * Loads the poster image via Glide (falling back to ic_event_poster on error),
         * formats the registration end date as "MMM d" for the date chip,
         * and wires up the card tap listener.
         *
         * @param event the event to display on this card
         */
        void bind(Event event) {
            tvTitle.setText(event.getTitle() != null ? event.getTitle() : "");

            // Format registration end date for the date chip
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

            // Navigate to EventDetailsFragment on tap
            itemView.setOnClickListener(v -> {
                if (clickListener != null && event.getEventId() != null) {
                    clickListener.onCarouselEventClick(event);
                }
            });
        }
    }
}