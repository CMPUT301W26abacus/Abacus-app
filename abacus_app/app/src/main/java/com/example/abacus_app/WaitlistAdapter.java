package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Adapter for displaying the list of entrants on a waitlist.
 * Uses item_entrant.xml layout.
 * Owner: Himesh
 */
public class WaitlistAdapter extends RecyclerView.Adapter<WaitlistAdapter.WaitlistViewHolder> {

    private final List<WaitlistEntry> entries;

    public WaitlistAdapter(List<WaitlistEntry> entries) {
        this.entries = entries;
    }

    @NonNull
    @Override
    public WaitlistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_entrant, parent, false);
        return new WaitlistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WaitlistViewHolder holder, int position) {
        WaitlistEntry entry = entries.get(position);
        holder.tvName.setText("User: " + entry.getUserId());
        holder.tvStatus.setText("Status: " + entry.getStatus());
        
        // Cancel button visibility logic (e.g. only if not already cancelled)
        // holder.btnCancel.setVisibility(View.VISIBLE);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class WaitlistViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus;
        Button btnCancel;

        WaitlistViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            tvStatus = itemView.findViewById(R.id.tv_entrant_status);
            btnCancel = itemView.findViewById(R.id.btn_cancel_entrant);
        }
    }
}
