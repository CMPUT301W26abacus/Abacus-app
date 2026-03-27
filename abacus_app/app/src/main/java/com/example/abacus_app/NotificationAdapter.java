package com.example.abacus_app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter class for the RecyclerView in NotificationFragment.
 * This class follows the Adapter design pattern to bind Notification data to UI components.
 * It manages a list of Notification objects and provides the logic for displaying them in a list.
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    public interface OnNotificationActionListener {
        void onAccept(Notification notification);
        void onDecline(Notification notification);
    }

    private List<Notification> notifications = new ArrayList<>();
    private OnNotificationActionListener actionListener;

    public void setOnNotificationActionListener(OnNotificationActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Updates the data set of the adapter and refreshes the UI.
     *
     * @param notifications The new list of notifications to display.
     */
    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        holder.messageTextView.setText(notification.getMessage());
        
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateString = sdf.format(new Date(notification.getTimestamp()));
        holder.timestampTextView.setText(dateString);

        if ("CO_ORGANIZER_INVITE".equals(notification.getType())) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.btnAccept.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onAccept(notification);
            });
            holder.btnDecline.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onDecline(notification);
            });
        } else {
            holder.layoutActions.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            String eventId = notification.getEventId();
            if (eventId != null && !eventId.isEmpty()) {
                Bundle args = new Bundle();
                args.putString(EventDetailsFragment.ARG_EVENT_ID, eventId);
                Navigation.findNavController(v).navigate(R.id.eventDetailsFragment, args);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    /**
     * ViewHolder class for individual notification items.
     * Holds references to the views within each item layout.
     */
    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timestampTextView;
        View layoutActions;
        MaterialButton btnAccept, btnDecline;

        /**
         * Constructs a ViewHolder and initializes its view references.
         *
         * @param itemView The root view of the notification item layout.
         */
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.notificationMessage);
            timestampTextView = itemView.findViewById(R.id.notificationTimestamp);
            layoutActions = itemView.findViewById(R.id.layout_notification_actions);
            btnAccept = itemView.findViewById(R.id.btn_accept_notification);
            btnDecline = itemView.findViewById(R.id.btn_decline_notification);
        }
    }
}
