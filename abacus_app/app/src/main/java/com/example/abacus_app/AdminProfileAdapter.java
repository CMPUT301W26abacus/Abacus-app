package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * Adapter for the admin profile moderation list.
 * Shows name, email, and role.
 */
public class AdminProfileAdapter extends RecyclerView.Adapter<AdminProfileAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDelete(User user);
    }

    private final List<User> users;
    private final OnDeleteClickListener listener;

    public AdminProfileAdapter(List<User> users, OnDeleteClickListener listener) {
        this.users    = users;
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
        User user = users.get(position);

        holder.tvPrimary.setText(user.getName() != null ? user.getName() : "No Name");
        holder.tvSecondary.setText(user.getEmail() != null ? user.getEmail() : "No Email");
        holder.tvTertiary.setText("Role: " + (user.getRole() != null ? user.getRole() : "entrant"));

        // No image for profiles — use account circle placeholder
        holder.ivThumb.setImageResource(R.drawable.ic_account_circle);
        holder.ivThumb.setBackgroundResource(0);

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(user);
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

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