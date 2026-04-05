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
 * Adapter class for the RecyclerView in NotificationFragment.
 * Supports two view types: Normal (My Inbox) and Log (Admin view).
 */
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_LOG = 1;

    public interface OnNotificationActionListener {
        void onAccept(Notification notification);
        void onDecline(Notification notification);
    }

    public interface OnItemClickListener {
        void onItemClick(String eventId);
    }

    private List<Notification> notifications = new ArrayList<>();
    private Map<String, String> organizerEmails = new HashMap<>();
    private Map<String, String> eventTitles = new HashMap<>();
    private OnNotificationActionListener actionListener;
    private OnItemClickListener itemClickListener;
    private boolean isReadOnly = false;

    public void setOnNotificationActionListener(OnNotificationActionListener listener) {
        this.actionListener = listener;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
        notifyDataSetChanged();
    }

    public void setOrganizerEmails(Map<String, String> emails) {
        this.organizerEmails = emails;
        notifyDataSetChanged();
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return isReadOnly ? VIEW_TYPE_LOG : VIEW_TYPE_NORMAL;
    }

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

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView, timestampTextView, statusTextView;
        View layoutActions;
        MaterialButton btnAccept, btnDecline;

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

    static class LogViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView, timestampTextView, statusTextView, tvRecipient, tvSender, tvType;

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
