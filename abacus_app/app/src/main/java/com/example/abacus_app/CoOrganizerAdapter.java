package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying co-organizers in a horizontal list.
 */
public class CoOrganizerAdapter extends RecyclerView.Adapter<CoOrganizerAdapter.ViewHolder> {

    private final List<User> coOrganizers;

    public CoOrganizerAdapter(List<User> coOrganizers) {
        this.coOrganizers = coOrganizers;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_co_organizer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = coOrganizers.get(position);
        holder.tvName.setText(user.getName() != null ? user.getName() : "Unknown");
    }

    @Override
    public int getItemCount() {
        return coOrganizers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_co_organizer_name);
        }
    }
}
