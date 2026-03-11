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
 * Shows event name, organizer ID, and date posted.
 */
public class AdminImageAdapter extends RecyclerView.Adapter<AdminImageAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDelete(Event event);
    }

    private final List<Event> events;
    private final OnDeleteClickListener listener;
    private final SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public AdminImageAdapter(List<Event> events, OnDeleteClickListener listener) {
        this.events   = events;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);

        holder.tvPrimary.setText(event.getTitle() != null ? event.getTitle() : "Untitled");
        holder.tvSecondary.setText("Organizer: " + (event.getOrganizerId() != null
                ? event.getOrganizerId() : "Unknown"));

        String date = "Date: —";
        if (event.getRegistrationStart() != null) {
            date = "Posted: " + sdf.format(event.getRegistrationStart().toDate());
        }
        holder.tvTertiary.setText(date);

        // Load poster thumbnail if Glide is available
        try {
            com.bumptech.glide.Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_event_poster)
                    .into(holder.ivThumb);
        } catch (Exception ignored) {}

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(event);
        });
    }

    @Override
    public int getItemCount() { return events.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        android.widget.ImageView ivThumb;
        TextView tvPrimary, tvSecondary, tvTertiary;
        MaterialButton btnDelete;

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