package com.example.abacus_app;

import android.content.Context;
import android.content.res.ColorStateList;
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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
 * - "Edit"   — white background, orange stroke, orange text (for organizers)
 * - "Manage" — orange background, white text (for co-organizers)
 *
 * Heart button:
 * - On bind, checks users/{uid}/saved/{eventId} to set filled/outlined state.
 * - Tapping toggles saved state in Firestore and updates the icon.
 * - Both authenticated and guest users can save events (guests are identified by device UUID).
 * - Hidden for admins, organizers, or when explicitly suppressed via setHideFavourite(true).
 * - Always uses ic_favourit drawable; grey tint when unsaved, orange tint when saved.
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

    /**
     * When true, the favourite/heart button is hidden regardless of user state.
     * Use setHideFavourite(true) for contexts like the Saved screen where the
     * heart is redundant.
     */
    private boolean hideFavourite = false;

    // ── Constructors ──────────────────────────────────────────────────────────

    public EventAdapter(List<Event> events,
                        OnEventClickListener clickListener,
                        OnEventDeleteListener deleteListener,
                        boolean isAdmin,
                        boolean canManageEvents,
                        @Nullable String userKey,
                        boolean isGuest) {
        this.events          = events;
        this.clickListener   = clickListener;
        this.deleteListener  = deleteListener;
        this.isAdmin         = isAdmin;
        this.canManageEvents = canManageEvents;
        this.userKey         = userKey;
        this.isGuest         = isGuest;
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

    /**
     * When set to true, hides the favourite/heart button on all cards.
     * Useful for contexts like the Saved screen where the button is redundant.
     *
     * @param hide true to hide the heart button
     */
    public void setHideFavourite(boolean hide) {
        this.hideFavourite = hide;
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

        // ── Description ────────────────────────────────────────────────────────
        if (event.getDescription() != null && !event.getDescription().isEmpty()) {
            holder.tvDescription.setText(event.getDescription());
            holder.tvDescription.setVisibility(View.VISIBLE);
        } else {
            holder.tvDescription.setVisibility(View.GONE);
        }

        // ── Start date/time ────────────────────────────────────────────────────
        if (event.getRegistrationStart() != null) {
            java.util.Date date = event.getRegistrationStart().toDate();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "MMM dd, yyyy h:mm a", java.util.Locale.getDefault());
            holder.tvDatetime.setText("Start: " + sdf.format(date));
            holder.tvDatetime.setVisibility(View.VISIBLE);
        } else {
            holder.tvDatetime.setVisibility(View.GONE);
        }

        // ── Poster image ───────────────────────────────────────────────────────
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

        // ── Heart / saved button ───────────────────────────────────────────────
        // Hidden when explicitly suppressed (e.g. Saved screen) or no event ID.
        // Both guests and authenticated users can save events.
        if (hideFavourite || event.getEventId() == null) {
            holder.btnFavourite.setVisibility(View.GONE);
        } else {
            holder.btnFavourite.setVisibility(View.VISIBLE);
            setupSaveButton(holder, event);
        }

        // Card tap — open details, no auto-join
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
        });

        // ── Organizer check ────────────────────────────────────────────────────
        String organizerId = event.getOrganizerId();
        boolean isOrganizerByUUID = userKey != null && userKey.equals(organizerId);
        boolean isOrganizerByFirebaseUID = false;

        if (!isOrganizerByUUID && canManageEvents) {
            // Also check Firebase UID (organizers are created with Firebase UID as organizerId)
            FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
            isOrganizerByFirebaseUID = (firebaseUser != null && firebaseUser.getUid().equals(organizerId));
        }

        if (canManageEvents && (isOrganizerByUUID || isOrganizerByFirebaseUID)) {
            // Organizer mode: show "Edit" instead of "Join", hide heart
            applyEditButtonState(holder, holder.itemView.getContext());
            holder.btnFavourite.setVisibility(View.GONE);
            holder.btnJoinStatus.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onEventClick(event.getTitle(), false);
                }
            });
            return;
        }

        // ── Join status check ──────────────────────────────────────────────────
        String eventId = event.getEventId();

        if (userKey == null || eventId == null || eventId.isEmpty()) {
            applyButtonState(holder, ButtonState.JOIN, holder.itemView.getContext());
            holder.btnJoinStatus.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onEventClick(event.getTitle(), false);
            });
            return;
        }

        // Check if user is a co-organizer — coOrganizers stores Firebase UID
        FirebaseUser fbUser = FirebaseAuth.getInstance().getCurrentUser();
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

        // Organizers cannot join other people's events — only edit/manage their own.
        // Heart also hidden here since organizers don't participate in events.
        if (canManageEvents) {
            holder.btnJoinStatus.setVisibility(View.GONE);
            holder.btnFavourite.setVisibility(View.GONE);
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
                    applyButtonState(holder, joined ? ButtonState.JOINED : ButtonState.JOIN,
                            holder.itemView.getContext());

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

    // ── Save / heart button ───────────────────────────────────────────────────

    /**
     * Checks Firestore to set the correct heart icon, then wires up toggle on tap.
     * Saved state stored at: users/{uid}/saved/{eventId} for authenticated users.
     * Both guests (identified by device UUID via userKey) and authenticated users
     * can save events.
     *
     * Always uses ic_favourite drawable — grey tint when unsaved, orange tint when saved.
     * This ensures consistent icon sizing regardless of saved state.
     */
    private void setupSaveButton(@NonNull EventViewHolder holder, @NonNull Event event) {
        // Use Firebase UID if available, otherwise fall back to device UUID (guest)
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        String uid = currentUser != null ? currentUser.getUid() : userKey;

        if (uid == null) {
            holder.btnFavourite.setVisibility(View.GONE);
            return;
        }

        String eventId = event.getEventId();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Reset to unsaved state while async check runs
        holder.btnFavourite.setImageResource(R.drawable.ic_favourite);
        holder.btnFavourite.setColorFilter(android.graphics.Color.GRAY);

        db.collection("users").document(uid)
                .collection("saved").document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    int pos = holder.getBindingAdapterPosition();
                    if (pos == RecyclerView.NO_POSITION) return;

                    boolean isSaved = snapshot.exists();
                    setSaveIcon(holder, isSaved);

                    holder.btnFavourite.setOnClickListener(v ->
                            toggleSaved(holder, db, uid, eventId, event, isSaved));
                });
    }

    private void toggleSaved(@NonNull EventViewHolder holder,
                             @NonNull FirebaseFirestore db,
                             @NonNull String uid,
                             @NonNull String eventId,
                             @NonNull Event event,
                             boolean currentlySaved) {
        if (currentlySaved) {
            // Unsave
            db.collection("users").document(uid)
                    .collection("saved").document(eventId)
                    .delete()
                    .addOnSuccessListener(unused -> {
                        setSaveIcon(holder, false);
                        holder.btnFavourite.setOnClickListener(v ->
                                toggleSaved(holder, db, uid, eventId, event, false));
                    });
        } else {
            // Save — store minimal event info so SavedFragment can display it
            java.util.Map<String, Object> data = new java.util.HashMap<>();
            data.put("eventId", eventId);
            data.put("title",   event.getTitle());
            data.put("savedAt", System.currentTimeMillis());
            db.collection("users").document(uid)
                    .collection("saved").document(eventId)
                    .set(data)
                    .addOnSuccessListener(unused -> {
                        setSaveIcon(holder, true);
                        holder.btnFavourite.setOnClickListener(v ->
                                toggleSaved(holder, db, uid, eventId, event, true));
                    });
        }
    }

    /**
     * Sets the save icon appearance. Always uses ic_favourite for consistent sizing.
     * Grey tint when unsaved, orange tint when saved.
     */
    private void setSaveIcon(@NonNull EventViewHolder holder, boolean saved) {
        if (saved) {
            holder.btnFavourite.setImageResource(R.drawable.ic_saved);
            holder.btnFavourite.setColorFilter(
                    ContextCompat.getColor(holder.itemView.getContext(), R.color.orange));
        } else {
            holder.btnFavourite.setImageResource(R.drawable.ic_favourite);
            holder.btnFavourite.setColorFilter(android.graphics.Color.GRAY);
        }
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
                btn.setBackgroundTintList(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.white)));
                btn.setStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(context, R.color.grey)));
                btn.setStrokeWidth(2);
                break;
            case MANAGE:
                btn.setText("Manage");
                btn.setTextColor(ContextCompat.getColor(context, R.color.white));
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
        btn.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.white)));
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
        TextView       tvDescription;
        TextView       tvDatetime;
        MaterialButton btnJoinStatus;
        MaterialButton btnDelete;
        ImageButton    btnFavourite;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster      = itemView.findViewById(R.id.iv_event_poster);
            tvTitle       = itemView.findViewById(R.id.tv_event_title);
            tvDescription = itemView.findViewById(R.id.tv_event_description);
            tvDatetime    = itemView.findViewById(R.id.tv_event_datetime);
            btnJoinStatus = itemView.findViewById(R.id.btn_join_status);
            btnDelete     = itemView.findViewById(R.id.btn_admin_delete);
            btnFavourite  = itemView.findViewById(R.id.imageButton2);
        }
    }
}