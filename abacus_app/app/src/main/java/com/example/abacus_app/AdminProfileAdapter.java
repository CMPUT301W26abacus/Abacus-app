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
 * RecyclerView adapter for the admin profile moderation screen.
 *
 * <p>Displays a scrollable list of {@link User} profiles for review by an administrator.
 * Each card shows the user's display name, email address, and role. A delete button triggers
 * a callback (typically a confirmation dialog) so the admin can soft-delete the profile.
 *
 * <p><b>Design pattern:</b> This adapter follows the standard ViewHolder pattern and delegates
 * all business logic to the calling fragment ({@link AdminLogsFragment.AdminTabFragment}) via
 * the {@link OnDeleteClickListener} callback interface. The adapter itself is stateless beyond
 * the list reference it holds.
 *
 * <p><b>Known issues / outstanding work:</b>
 * <ul>
 *   <li>The adapter calls {@code notifyDataSetChanged()} globally on every filter change.
 *       Replace with {@code DiffUtil} for better performance on large datasets.</li>
 *   <li>The profile image is always set to {@code ic_account_circle}. If user avatars are
 *       added to the data model, this binding will need to load from a URL (e.g. Glide/Coil).</li>
 *   <li>Guest users (those with no last-login timestamp) are not visually distinguished from
 *       regular users in this adapter.</li>
 * </ul>
 */
public class AdminProfileAdapter extends RecyclerView.Adapter<AdminProfileAdapter.ViewHolder> {

    // ── Callback interface ────────────────────────────────────────────────────

    /**
     * Callback fired when the admin taps the delete button on a profile card.
     * The caller is responsible for showing a confirmation dialog before committing the deletion.
     */
    public interface OnDeleteClickListener {
        /**
         * Called when the delete button for {@code user} is clicked.
         *
         * @param user the {@link User} whose card was actioned; never {@code null}
         */
        void onDelete(User user);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Live, filtered list of users supplied by the parent fragment. */
    private final List<User> users;

    /** Callback invoked when the delete button on any card is tapped. */
    private final OnDeleteClickListener listener;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new adapter.
     *
     * @param users    the mutable list of {@link User} objects to display; mutations to this
     *                 list must be followed by a call to {@link #notifyDataSetChanged()}
     * @param listener callback invoked when the delete button is tapped; may be {@code null},
     *                 in which case tapping delete is a no-op
     */
    public AdminProfileAdapter(List<User> users, OnDeleteClickListener listener) {
        this.users    = users;
        this.listener = listener;
    }

    // ── RecyclerView.Adapter overrides ────────────────────────────────────────

    /**
     * Inflates the shared {@code item_admin_card} layout and wraps it in a {@link ViewHolder}.
     *
     * @param parent   the RecyclerView this view will be attached to
     * @param viewType unused; only one view type is supported
     * @return a new, unbound {@link ViewHolder}
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_card, parent, false);
        return new ViewHolder(view);
    }

    /**
     * Binds a {@link User} to the card at {@code position}.
     *
     * <p>Falls back to safe placeholder strings when name or email are {@code null}.
     * Role defaults to {@code "entrant"} when absent.
     *
     * @param holder   the {@link ViewHolder} to populate
     * @param position the index of the user in {@link #users}
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);

        holder.tvPrimary.setText(user.getName() != null ? user.getName() : "No Name");
        holder.tvSecondary.setText(user.getEmail() != null ? user.getEmail() : "No Email");
        holder.tvTertiary.setText("Role: " + (user.getRole() != null ? user.getRole() : "entrant"));

        // No avatar URL support yet — always use the generic account-circle drawable.
        holder.ivThumb.setImageResource(R.drawable.ic_account_circle);
        holder.ivThumb.setBackgroundResource(0);

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(user);
        });
    }

    /**
     * Returns the total number of user cards to display.
     *
     * @return size of the backing list
     */
    @Override
    public int getItemCount() { return users.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    /**
     * Caches the child views of a single admin profile card to avoid repeated
     * {@link View#findViewById} calls during scrolling.
     */
    static class ViewHolder extends RecyclerView.ViewHolder {

        /** Circular avatar placeholder image. */
        android.widget.ImageView ivThumb;

        /** User's display name (primary line). */
        TextView tvPrimary;

        /** User's email address (secondary line). */
        TextView tvSecondary;

        /** User's role label, prefixed with "Role: " (tertiary line). */
        TextView tvTertiary;

        /** Delete button; triggers {@link OnDeleteClickListener#onDelete(User)}. */
        MaterialButton btnDelete;

        /**
         * Constructs the ViewHolder and resolves all child view references.
         *
         * @param itemView the inflated card layout
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