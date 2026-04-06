package com.example.abacus_app;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/**
 * Adapter for {@link Comment} objects to the RecyclerView in {@link CommentsFragment} for display
 * of event comment sections. Calculates difference in comment timestamps and present time.
 *
 * @author Kaylee
 */
public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private List<Comment> comments;
    private boolean canDelete;
    private final CommentRepository repo = new CommentRepository();

    /**
     * Constructs the adapter.
     * @param comments the list of comments to display
     */
    public CommentAdapter(List<Comment> comments) {
        this.comments = comments;
        this.canDelete = false;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username, timestamp, content;
        ImageButton btn_delete;

        public ViewHolder(View view) {
            super(view);
            username = view.findViewById(R.id.tv_username_comment);
            timestamp = view.findViewById(R.id.tv_timestamp_comment);
            content = view.findViewById(R.id.tv_comment_text);
            btn_delete = view.findViewById(R.id.btn_delete_comment);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_comment_box, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {

        Comment c = comments.get(position);

        String username = c.getUsername() == null ? "Unknown" : c.getUsername();

        holder.username.setText(username);
        holder.content.setText(c.getContent());

        // format timestamp
        if (c.getTimestamp() != null) {
            //Date date = c.getTimestamp().toDate();
            holder.timestamp.setText(
                    getTimeAgo(c.getTimestamp())
            );
        }

        // delete button setup
        if (canDelete) {
            holder.btn_delete.setVisibility(View.VISIBLE);
        } else {
            holder.btn_delete.setVisibility(View.GONE);
        }

        holder.btn_delete.setOnClickListener(v -> {
            repo.deleteComment(c.getEventId(), c.getCommentId(), new CommentRepository.VoidCallback() {
                @Override
                public void onComplete(Exception error) {
                    if (error != null) {
                        Log.d("CommentAdapter", "deleteComment: error deleting comment");
                        return;
                    }
                    comments.remove(c);
                    notifyDataSetChanged();

                    // Notify the comment author that their comment was removed.
                    String authorId = c.getUserId();
                    if (authorId != null && !authorId.isEmpty()) {
                        Notification n = new Notification(
                                authorId,
                                null,
                                c.getEventId(),
                                "Your comment was removed by a moderator.",
                                Notification.TYPE_COMMENT_DELETED
                        );
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("notifications")
                                .add(n)
                                .addOnFailureListener(e ->
                                        Log.e("CommentAdapter", "Failed to send comment-deleted notification", e));
                    }
                }
            });
        });
    }

    /**
     * Gets the number of comments in the list.
     * @return the number of comments in the list
     */
    @Override
    public int getItemCount() {
        return comments.size();
    }

    /**
     * Formats comment display time according to difference between time comment was posted and
     * current time.
     *
     * @param timestamp time comment was posted
     * @return formated time display
     */
    public static String getTimeAgo(long timestamp) {
        long now = System.currentTimeMillis();
        long diff = now - timestamp;

        long seconds = diff / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        long weeks = days / 7;

        if (seconds < 10) return "now";
        if (seconds < 60) return seconds + "s ago";
        if (minutes < 60) return minutes + "m ago";
        if (hours < 24) return hours + "h ago";
        if (days < 7) return days + "d ago";
        return weeks + "w ago";
    }

    /**
     * Sets boolean canDelete, which depends on user role and event and may come later as data must
     * be fetched from database.
     *
     * @param canDelete whether or not the current user has delete capabilities
     */
    public void setCanDelete(boolean canDelete) {
        this.canDelete = canDelete;
        notifyDataSetChanged(); // refresh for change
    }
}
