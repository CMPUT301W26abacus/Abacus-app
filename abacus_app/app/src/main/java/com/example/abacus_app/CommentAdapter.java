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
            username = view.findViewById(R.id.usernameText);
            timestamp = view.findViewById(R.id.timestampText);
            content = view.findViewById(R.id.commentText);
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

        holder.username.setText(c.getUserId());
        holder.content.setText(c.getContent());

        // format timestamp
        if (c.getTimestamp() != null) {
            //Date date = c.getTimestamp().toDate();
            holder.timestamp.setText(
                    c.getTimestamp().toString()
            );
        }
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }
}
