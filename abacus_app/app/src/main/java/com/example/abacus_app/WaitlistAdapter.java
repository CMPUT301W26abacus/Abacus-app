package com.example.abacus_app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for displaying the list of entrants on a waitlist.
 * Uses item_entrant.xml layout.
 * Owner: Himesh
 */
public class WaitlistAdapter extends RecyclerView.Adapter<WaitlistAdapter.WaitlistViewHolder> {

    private final List<WaitlistEntry> entries;
    private final RegistrationRepository registrationRepository = new RegistrationRepository();
    private final NotificationRepository notificationRepository = new NotificationRepository();

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

        // set display name
        String displayName = entry.getUserName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "Guest: " + entry.getUserID().split("_", 2)[0];
        }
        holder.tvName.setText(displayName);

        // set join time
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        String dateString = entry.getTimestamp() != null ? sdf.format(new Date(entry.getTimestamp())) : "Unknown";
        holder.tvJoinTime.setText("Joined: " + dateString);


        // set status color and text (disable cancel button if applicable)
        String status = entry.getStatus();
        Context context = holder.itemView.getContext();
        switch (status) {
            case WaitlistEntry.STATUS_WAITLISTED:
                holder.tvStatus.setText("Waitlisted");
                holder.tvStatus.getBackground().setTint(ContextCompat.getColor(context, R.color.color_status_waitlisted_orange));
                holder.btnCancel.setEnabled(true);
                holder.btnCancel.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error_red)));
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_grey));
                break;
            case WaitlistEntry.STATUS_INVITED:
                holder.tvStatus.setText("Invited");
                holder.tvStatus.getBackground().setTint(ContextCompat.getColor(context, R.color.color_status_invited_blue));
                holder.btnCancel.setEnabled(true);
                holder.btnCancel.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error_red)));
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_grey));
                break;
            case WaitlistEntry.STATUS_ACCEPTED:
                holder.tvStatus.setText("Accepted");
                holder.tvStatus.getBackground().setTint(ContextCompat.getColor(context, R.color.color_status_accepted_green));
                holder.btnCancel.setEnabled(true);
                holder.btnCancel.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.error_red)));
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.light_grey));
                break;
            case WaitlistEntry.STATUS_DECLINED:
                holder.tvStatus.setText("Declined");
                holder.tvStatus.getBackground().setTint(ContextCompat.getColor(context, R.color.color_status_declined_red));
                holder.btnCancel.setEnabled(false);
                holder.btnCancel.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grey)));
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.card_stroke));
                break;
            case WaitlistEntry.STATUS_CANCELLED:
                holder.tvStatus.setText("Cancelled");
                holder.tvStatus.getBackground().setTint(ContextCompat.getColor(context, R.color.color_status_canceled_black));
                holder.btnCancel.setEnabled(false);
                holder.btnCancel.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.grey)));
                holder.cardView.setCardBackgroundColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.card_stroke));
                break;
        }

        // set up onClick listener for cancel entrant button
        final String cancelEntrantName = entry.getUserName() == null ? "this user" : entry.getUserName();
        holder.btnCancel.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(v.getContext())
                    .setTitle(String.format("Cancel %s?", cancelEntrantName))
                    .setMessage(String.format(
                            context.getString(R.string.confirm_cancel_entrant), cancelEntrantName))

                    .setPositiveButton("YES", (d, which) -> {
                        // DO THE CANCEL HERE
                        registrationRepository.cancelEntrant(entry.getUserID(), entry.getEventID(), new RegistrationRepository.VoidCallback() {
                            @Override
                            public void onComplete(Exception error) {
                                entry.setStatus(WaitlistEntry.STATUS_CANCELLED);
                                notifyDataSetChanged();
                                notificationRepository.notifyCancelled(entry.getEventID(), entry.getUserID(), e -> {});
                            }
                        });
                    })

                    .setNegativeButton("NO", (d, which) -> {
                        d.dismiss();
                    })

                    .create();
            dialog.show();
            // alert dialog styling
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.bg_alert_dialog_round);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(Color.RED);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(Color.BLACK);
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class WaitlistViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvJoinTime, tvStatus;
        ImageButton btnCancel;
        CardView cardView;

        WaitlistViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            tvJoinTime = itemView.findViewById(R.id.tv_entrant_jointime);
            tvStatus = itemView.findViewById(R.id.tv_entrant_status);
            btnCancel = itemView.findViewById(R.id.btn_cancel_entrant);
            cardView = itemView.findViewById(R.id.cv_entrant_card);
        }
    }
}
