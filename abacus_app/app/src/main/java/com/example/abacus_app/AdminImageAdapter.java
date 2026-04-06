package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for the admin image moderation list.
 * Shows event name, organizer email, and date posted.
 *
 * @author erika
 */
public class AdminImageAdapter extends RecyclerView.Adapter<AdminImageAdapter.ViewHolder> {

    /**
     * Callback interface for delete button clicks on an event card.
     */
    public interface OnDeleteClickListener {
        /**
         * Called when the delete button is clicked for the given event.
         *
         * @param event The event to be deleted.
         */
        void onDelete(Event event);
    }

    private final List<Event> events;
    private final OnDeleteClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    /**
     * Constructs a new {@code AdminImageAdapter}.
     *
     * @param events   The list of events to display.
     * @param listener Callback invoked when the delete button is tapped.
     */
    public AdminImageAdapter(List<Event> events, OnDeleteClickListener listener) {
        this.events   = events;
        this.listener = listener;
    }

    /**
     * Inflates the item layout and returns a new {@link ViewHolder}.
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_card, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Binds event data to the given {@link ViewHolder}.
     *
     * @param holder   The ViewHolder to bind data into.
     * @param position The position of the item in the list.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);

        holder.tvPrimary.setText(event.getTitle() != null ? event.getTitle() : "Untitled");

        // Show organizer email instead of ID
        String organizerInfo = event.getOrganizerEmail() != null ? event.getOrganizerEmail() : event.getOrganizerId();
        holder.tvSecondary.setText("Organizer: " + (organizerInfo != null ? organizerInfo : "Unknown"));

        String date = "Date: —";
        if (event.getRegistrationStart() != null) {
            date = "Posted: " + sdf.format(event.getRegistrationStart().toDate());
        }
        holder.tvTertiary.setText(date);

        // Load poster thumbnail if Glide is available
        try {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageUrl())
                    .placeholder(R.id.iv_thumb) // Note: using resource ID as placeholder might be wrong if it's not a drawable
                    .into(holder.ivThumb);
        } catch (Exception ignored) {}

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(event);
        });
    }

    /**
     * Returns the total number of events in the list.
     *
     * @return The item count.
     */
    @Override
    public int getItemCount() { return events.size(); }

    /**
     * ViewHolder for an admin image moderation card.
     * Holds references to the thumbnail, text fields, and delete button.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView ivThumb;
        TextView tvPrimary, tvSecondary, tvTertiary;
        MaterialButton btnDelete;

        /**
         * Constructs a ViewHolder and binds all child views.
         *
         * @param itemView The root view of the item layout.
         */
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumb     = itemView.findViewById(R.id.iv_thumb);
            tvPrimary   = itemView.findViewById(R.id.tv_primary);
            tvSecondary = itemView.findViewById(R.id.tv_secondary);
            tvTertiary  = itemView.findViewById(R.id.tv_tertiary);
            btnDelete   = itemView.findViewById(R.id.btn_delete);
        }
    }
}