package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Adapter for the organizer's entrant browsing list.
 * Allows inviting specific entrants to private events or as co-organizers.
 */
public class OrganizerEntrantAdapter extends RecyclerView.Adapter<OrganizerEntrantAdapter.ViewHolder> {

    public interface OnInviteClickListener {
        void onInvite(User user);
    }

    private final List<User> users;
    private final OnInviteClickListener inviteListener;

    public OrganizerEntrantAdapter(List<User> users, OnInviteClickListener inviteListener) {
        this.users = users;
        this.inviteListener = inviteListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_entrant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);

        holder.tvName.setText(user.getName() != null && !user.getName().isEmpty() ? user.getName() : "Anonymous User");
        
        StringBuilder info = new StringBuilder();
        if (user.getEmail() != null && !user.getEmail().isEmpty()) info.append(user.getEmail());
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            if (info.length() > 0) info.append(" • ");
            info.append(user.getPhone());
        }
        holder.tvInfo.setText(info.toString());

        holder.btnInvite.setOnClickListener(v -> {
            if (inviteListener != null) inviteListener.onInvite(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvInfo;
        MaterialButton btnInvite;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_entrant_avatar);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            tvInfo = itemView.findViewById(R.id.tv_entrant_info);
            btnInvite = itemView.findViewById(R.id.btn_invite);
        }
    }
}