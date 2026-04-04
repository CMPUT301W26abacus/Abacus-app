package com.example.abacus_app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

/**
 * EventAdapter.java
 *
 * Binds a list of Event objects to item_event cards in the home RecyclerView.
 *
 * Join button behaviour:
 * - On bind, queries Firestore to set the button visual state (Join/Joined/Manage).
 * - Tapping the card opens EventDetailsFragment normally.
 * - Tapping the Join button opens EventDetailsFragment with ARG_AUTO_JOIN=true,
 *   which tells EventDetailsFragment to immediately trigger the join flow as
 *   soon as the user ID is resolved — no extra tap needed.
 * - Tapping the Joined button opens EventDetailsFragment normally (user can
 *   leave from there if they want).
 * - Tapping the Manage button (for co-organizers) opens the event management screen.
 *
 * Visual states:
 * - "Join"   — orange background, white text
 * - "Joined" — white background, grey stroke, grey text
 * - "Edit"   — white background, blue stroke, blue text (for organizers)
 * - "Manage" — blue background, white text
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(String eventTitle, boolean autoJoin);
    }

    public interface OnEventDeleteListener {
        void onEventDelete(Event event);
    }

    public interface OnManageClickListener {
        void onManageClick(Event event);
    }

    private final List<Event> events;
    private final OnEventClickListener clickListener;
    private final OnEventDeleteListener deleteListener;
    private OnManageClickListener manageClickListener;
    private final boolean isAdmin;
    private final boolean canManageEvents;

    @Nullable
    private final String userKey;
    private final boolean isGuest;

    // ── Constructors ──────────────────────────────────────────────────────────

    public EventAdapter(List<Event> events,
                        OnEventClickListener clickListener,
                        OnEventDeleteListener deleteListener,
                        boolean isAdmin,
                        boolean canManageEvents,
                        @Nullable String userKey,
                        boolean isGuest) {
        this.events         = events;
        this.clickListener  = clickListener;
        this.deleteListener = deleteListener;
        this.isAdmin        = isAdmin;
        this.canManageEvents = canManageEvents;
        this.userKey        = userKey;
        this.isGuest        = isGuest;
    }

    public EventAdapter(List<Event> events,
                        OnEventClickListener clickListener,
                        OnEventDeleteListener deleteListener,
                        OnManageClickListener manageClickListener,
                        boolean isAdmin,
                        boolean canManageEvents,
                        @Nullable String userKey,
                        boolean isGuest) {
        this(events, clickListener, deleteListener, isAdmin, canManageEvents, userKey, isGuest);
        this.manageClickListener = manageClickListener;
    }

    public EventAdapter(List<Event> events, OnEventClickListener clickListener) {
        this(events, clickListener, null, false, false, null, false);
    }

    // ── RecyclerView overrides ────────────────────────────────────────────────

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);

        holder.tvTitle.setText(event.getTitle() != null ? event.getTitle() : "");

        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_event_poster)
                    .error(R.drawable.ic_event_poster)
                    .centerCrop()
                    .into(holder.ivPoster);
        } else {
            holder.ivPoster.setImageResource(R.drawable.ic_event_poster);
        }

        // ── Admin mode ─────────────────────────────────────────────────────────
        if (isAdmin && deleteListener != null) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnJoinStatus.setVisibility(View.GONE);
            holder.btnFavourite.setVisibility(View.GONE);
            holder.btnDelete.setOnClickListener(v -> deleteListener.onEventDelete(event));
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
            });
            return;
        }

        // ── Normal mode ────────────────────────────────────────────────────────
        holder.btnDelete.setVisibility(View.GONE);
        holder.btnJoinStatus.setVisibility(View.VISIBLE);
        holder.btnFavourite.setVisibility(View.VISIBLE);

        // Card tap — open details, no auto-join
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
        });

        // Check if current user is the organizer of this event
        String organizerId = event.getOrganizerId();
        boolean isOrganizerByUUID = userKey != null && userKey.equals(organizerId);
        boolean isOrganizerByFirebaseUID = false;

        if (!isOrganizerByUUID && canManageEvents) {
            // Also check Firebase UID (organizers are created with Firebase UID as organizerId)
            com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            isOrganizerByFirebaseUID = (firebaseUser != null && firebaseUser.getUid().equals(organizerId));
        }

        if (canManageEvents && (isOrganizerByUUID || isOrganizerByFirebaseUID)) {
            // Organizer mode: show "Edit" instead of "Join"
            applyEditButtonState(holder, holder.itemView.getContext());
            holder.btnJoinStatus.setOnClickListener(v -> {
                if (clickListener != null) {
                    // Navigate to event details, which will then allow editing
                    clickListener.onEventClick(event.getTitle(), false);
                }
            });
            return;
        }

        // ── Join status check ──────────────────────────────────────────────────
        String eventId = event.getEventId();

        if (userKey == null || eventId == null || eventId.isEmpty()) {
            applyButtonState(holder, ButtonState.JOIN, holder.itemView.getContext());
            // Join button tap with no user key — open details without auto-join
            holder.btnJoinStatus.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
            });
            return;
        }

        // Check if user is a co-organizer — coOrganizers stores Firebase UID
        com.google.firebase.auth.FirebaseUser fbUser =
                com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
        String firebaseUid = fbUser != null ? fbUser.getUid() : null;
        boolean isCoOrganizer = event.getCoOrganizers() != null
                && ((firebaseUid != null && event.getCoOrganizers().contains(firebaseUid))
                || (userKey != null && event.getCoOrganizers().contains(userKey)));
        if (isCoOrganizer) {
            applyButtonState(holder, ButtonState.MANAGE, holder.itemView.getContext());
            holder.btnJoinStatus.setOnClickListener(v -> {
                if (manageClickListener != null) manageClickListener.onManageClick(event);
            });
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
            });
            return;
        }

        // Organizers cannot join other people's events — only edit/manage their own
        if (canManageEvents) {
            holder.btnJoinStatus.setVisibility(View.GONE);
            return;
        }

        // Reset to Join while async check runs to avoid stale recycled state
        applyButtonState(holder, ButtonState.JOIN, holder.itemView.getContext());
        holder.btnJoinStatus.setOnClickListener(null); // clear during check

        // Authenticated users: Check status in the event's waitlist subcollection
        FirebaseFirestore.getInstance()
                .collection("events")
                .document(eventId)
                .collection("waitlist")
                .document(userKey)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_ID || pos < 0 || pos >= events.size()) return;
                    if (!eventId.equals(events.get(pos).getEventId())) return;

                    boolean joined = snapshot.exists();
                    applyButtonState(holder, joined ? ButtonState.JOINED : ButtonState.JOIN, holder.itemView.getContext());

                    if (joined) {
                        // Already joined — tap opens details so they can leave from there
                        holder.btnJoinStatus.setOnClickListener(v -> {
                            if (clickListener != null)
                                clickListener.onEventClick(event.getTitle(), false);
                        });
                    } else {
                        // Not joined — tap opens details AND triggers join immediately
                        holder.btnJoinStatus.setOnClickListener(v -> {
                            if (clickListener != null)
                                clickListener.onEventClick(event.getTitle(), true);
                        });
                    }
                });
    }

    // ── Button appearance ─────────────────────────────────────────────────────

    private enum ButtonState { JOIN, JOINED, MANAGE }

    private void applyButtonState(@NonNull EventViewHolder holder,
                                  ButtonState state,
                                  @NonNull Context context) {
        MaterialButton btn = holder.btnJoinStatus;
        switch (state) {
            case JOINED:
                btn.setText("Joined");
                btn.setTextColor(ContextCompat.getColor(context, R.color.grey));
                btn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)
                ));
                btn.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.grey)));
                btn.setStrokeWidth(2);
                break;
            case MANAGE:
                btn.setText("Manage");
                btn.setTextColor(ContextCompat.getColor(context, R.color.white)
                );
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.orange)));
                btn.setStrokeWidth(0);
                break;
            case JOIN:
            default:
                btn.setText("Join");
                btn.setTextColor(ContextCompat.getColor(context, R.color.white));
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.orange)));
                btn.setStrokeWidth(0);
                break;
        }
    }

    private void applyEditButtonState(@NonNull EventViewHolder holder,
                                      @NonNull Context context) {
        MaterialButton btn = holder.btnJoinStatus;
        btn.setText("Edit");
        btn.setTextColor(ContextCompat.getColor(context, R.color.orange));
        btn.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white)));
        btn.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.orange)));
        btn.setStrokeWidth(2);
    }

    @Override
    public int getItemCount() { return events.size(); }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class EventViewHolder extends RecyclerView.ViewHolder {
        ImageView      ivPoster;
        TextView       tvTitle;
        MaterialButton btnJoinStatus;
        MaterialButton btnDelete;
        ImageButton    btnFavourite;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster      = itemView.findViewById(R.id.iv_event_poster);
            tvTitle       = itemView.findViewById(R.id.tv_event_title);
            btnJoinStatus = itemView.findViewById(R.id.btn_join_status);
            btnDelete     = itemView.findViewById(R.id.btn_admin_delete);
            btnFavourite  = itemView.findViewById(R.id.imageButton2);
        }
    }
}
