package com.example.abacus_app;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NotificationAdapter.java
 *
 * This adapter manages the display of notification items in a RecyclerView.
 * It supports two distinct view modes:
 * 1. Normal Mode (My Inbox): For users to view and interact with their own notifications (e.g., accepting invitations).
 * 2. Log Mode (Admin View): A read-only view for administrators to audit all system notifications.
 *
 * Role: Adapter in the View Layer (MVVM).
 *
 * Outstanding Issues:
 * - Event title fetching happens inside onBindViewHolder; consider pre-fetching or moving to a ViewModel to avoid redundant Firestore calls.
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_LOG = 1;

    /**
     * Interface to handle user actions on notifications (e.g., Accept/Decline).
     */
    public interface OnNotificationActionListener {
        /**
         * Called when the user clicks 'Accept' on an invitation notification.
         * @param notification The notification object being acted upon.
         */
        void onAccept(Notification notification);

        /**
         * Called when the user clicks 'Decline' on an invitation notification.
         * @param notification The notification object being acted upon.
         */
        void onDecline(Notification notification);
    }

    /**
     * Interface to handle clicks on a notification item to navigate to details.
     */
    public interface OnItemClickListener {
        /**
         * Called when a notification item is clicked.
         * @param eventId The ID of the event associated with the notification.
         */
        void onItemClick(String eventId);
    }

    private List<Notification> notifications = new ArrayList<>();
    private Map<String, String> organizerEmails = new HashMap<>();
    private Map<String, String> eventTitles = new HashMap<>();
    private OnNotificationActionListener actionListener;
    private OnItemClickListener itemClickListener;
    private boolean isReadOnly = false;

    /**
     * Sets the listener for notification actions (Accept/Decline).
     * @param listener The listener implementation.
     */
    public void setOnNotificationActionListener(OnNotificationActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Sets the listener for item clicks.
     * @param listener The listener implementation.
     */
    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    /**
     * Updates the list of notifications to be displayed.
     * @param notifications The new list of notifications.
     */
    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    /**
     * Updates the cache of organizer emails for display in Log mode.
     * @param emails A map of organizer IDs to their emails.
     */
    public void setOrganizerEmails(Map<String, String> emails) {
        this.organizerEmails = emails;
        notifyDataSetChanged();
    }

    /**
     * Sets whether the adapter is in read-only (Log) mode or interactive (Inbox) mode.
     * @param readOnly True for read-only mode, false otherwise.
     */
    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    /**
     * Returns the view type of the item at position for the purposes of view recycling.
     * @param position position to query
     * @return integer value identifying the type of the view needed to represent the item at position.
     */
    @Override
    public int getItemViewType(int position) {
        return isReadOnly ? VIEW_TYPE_LOG : VIEW_TYPE_NORMAL;
    }

    /**
     * Called when RecyclerView needs a new {@link RecyclerView.ViewHolder} of the given type to represent an item.
     * @param parent The ViewGroup into which the new View will be added after it is bound to an adapter position.
     * @param viewType The view type of the new View.
     * @return A new ViewHolder that holds a View of the given view type.
     */
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LOG) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_log, parent, false);
            return new LogViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
            return new NotificationViewHolder(view);
        }
    }

    /**
     * Called by RecyclerView to display the data at the specified position.
     * @param holder The ViewHolder which should be updated to represent the contents of the item at the given position in the data set.
     * @param position The position of the item within the adapter's data set.
     */
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Notification notification = notifications.get(position);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateString = sdf.format(new Date(notification.getTimestamp()));

        if (holder instanceof LogViewHolder) {
            LogViewHolder logHolder = (LogViewHolder) holder;
            logHolder.messageTextView.setText(notification.getMessage());
            logHolder.timestampTextView.setText(dateString);
            logHolder.tvRecipient.setText("To: " + (notification.getUserEmail() != null ? notification.getUserEmail() : "Unknown"));
            
            // Resolve organizer email from cache or fallback to ID
            String orgId = notification.getOrganizerId();
            String email = organizerEmails.get(orgId);
            String senderDisplay = (email != null) ? email : (orgId != null ? orgId : "System/Unknown");
            
            logHolder.tvSender.setText("From: " + senderDisplay);

            // Fetch and show Event Title instead of Type
            String eventId = notification.getEventId();
            if (eventId != null && !eventId.isEmpty()) {
                if (eventTitles.containsKey(eventId)) {
                    logHolder.tvType.setText(eventTitles.get(eventId));
                } else {
                    logHolder.tvType.setText("Loading event...");
                    FirebaseFirestore.getInstance().collection("events").document(eventId)
                            .get()
                            .addOnSuccessListener(documentSnapshot -> {
                                String title = documentSnapshot.getString("title");
                                if (title != null) {
                                    eventTitles.put(eventId, title);
                                    notifyItemChanged(position);
                                } else {
                                    eventTitles.put(eventId, "Unknown Event");
                                    logHolder.tvType.setText("Unknown Event");
                                }
                            })
                            .addOnFailureListener(e -> {
                                eventTitles.put(eventId, "Error loading title");
                                logHolder.tvType.setText("Error loading title");
                            });
                }
            } else {
                logHolder.tvType.setText("System Notification");
            }

            // Hide status as requested
            logHolder.statusTextView.setVisibility(View.GONE);

        } else if (holder instanceof NotificationViewHolder) {
            NotificationViewHolder normalHolder = (NotificationViewHolder) holder;
            normalHolder.messageTextView.setText(notification.getMessage());
            normalHolder.timestampTextView.setText(dateString);

            if (Notification.TYPE_CO_ORGANIZER_INVITE.equals(notification.getType())) {
                String status = notification.getStatus();
                if (Notification.STATUS_PENDING.equals(status)) {
                    normalHolder.layoutActions.setVisibility(View.VISIBLE);
                    normalHolder.statusTextView.setVisibility(View.GONE);
                    normalHolder.btnAccept.setOnClickListener(v -> {
                        if (actionListener != null) actionListener.onAccept(notification);
                    });
                    normalHolder.btnDecline.setOnClickListener(v -> {
                        if (actionListener != null) actionListener.onDecline(notification);
                    });
                } else {
                    normalHolder.layoutActions.setVisibility(View.GONE);
                    normalHolder.statusTextView.setVisibility(View.VISIBLE);
                    normalHolder.statusTextView.setText(status);
                    if (Notification.STATUS_ACCEPTED.equals(status)) {
                        normalHolder.statusTextView.setTextColor(Color.parseColor("#4CAF50"));
                    } else {
                        normalHolder.statusTextView.setTextColor(Color.parseColor("#F44336"));
                    }
                }
            } else {
                normalHolder.layoutActions.setVisibility(View.GONE);
                normalHolder.statusTextView.setVisibility(View.GONE);
            }

            normalHolder.itemView.setOnClickListener(v -> {
                String eventId = notification.getEventId();
                if (eventId != null && !eventId.isEmpty()) {
                    itemClickListener.onItemClick(eventId);
                }
            });
        }
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     * @return The total number of items in this adapter.
     */
    @Override
    public int getItemCount() {
        return notifications.size();
    }

    /**
     * ViewHolder for standard interactive notifications in the user's inbox.
     * Supports action buttons for invitations.
     */
    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView, timestampTextView, statusTextView;
        View layoutActions;
        MaterialButton btnAccept, btnDecline;

        /**
         * Initializes the standard notification view holder.
         * @param itemView The root view of the notification item layout.
         */
        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.notificationMessage);
            timestampTextView = itemView.findViewById(R.id.notificationTimestamp);
            statusTextView = itemView.findViewById(R.id.tv_notification_status);
            layoutActions = itemView.findViewById(R.id.layout_notification_actions);
            btnAccept = itemView.findViewById(R.id.btn_accept_notification);
            btnDecline = itemView.findViewById(R.id.btn_decline_notification);
        }
    }

    /**
     * ViewHolder for read-only log entries used in the Admin view.
     * Displays additional metadata like sender and recipient.
     */
    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView, timestampTextView, statusTextView, tvRecipient, tvSender, tvType;

        /**
         * Initializes the log notification view holder.
         * @param itemView The root view of the notification log item layout.
         */
        public LogViewHolder(@NonNull View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.notificationMessage);
            timestampTextView = itemView.findViewById(R.id.notificationTimestamp);
            statusTextView = itemView.findViewById(R.id.tv_notification_status);
            tvRecipient = itemView.findViewById(R.id.tv_log_recipient);
            tvSender = itemView.findViewById(R.id.tv_log_sender);
            tvType = itemView.findViewById(R.id.tv_log_type);
        }
    }
}
