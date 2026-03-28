package com.example.abacus_app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private List<Comment> comments;

    public CommentAdapter(List<Comment> comments) {
        this.comments = comments;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username, timestamp, content;

        public ViewHolder(View view) {
            super(view);
            username = view.findViewById(R.id.tv_username_comment);
            timestamp = view.findViewById(R.id.tv_timestamp_comment);
            content = view.findViewById(R.id.tv_comment_text);
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
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    /**
     * Formats comment display time according to difference between time comment was posted and
     * current time.
     *
     * @param timestamp time comment was posted
     * @return formmated time display
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
}
