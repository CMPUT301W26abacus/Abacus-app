package com.example.abacus_app;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fragment to display the comments on a specific event. Allows a user to add a comment.
 */
public class CommentsFragment extends BottomSheetDialogFragment {

    public static final String ARG_EVENT_ID = "eventId";

    private RecyclerView recyclerView;
    private EditText input;
    private ImageButton send;
    private android.view.View inputContainer;
    private TextView tvEmpty;

    private List<Comment> comments = new ArrayList<>();
    private CommentAdapter adapter;

    private CommentRepository repo = new CommentRepository();

    private String eventId = null;
    private User currentUser  = null;
    private boolean canDelete = false;

    /**
     * Sets up the fragment as a BottomSheet that covers most of the screen.
     */
    @Override
    public void onStart() {
        super.onStart();

        View view = getView();
        if (view != null) {
            View parent = (View) view.getParent();

            // 90% of screen height
            int height = (int) (Resources.getSystem().getDisplayMetrics().heightPixels * 0.9);
            parent.getLayoutParams().height = height;

            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(parent);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_comments, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {

        Bundle args = getArguments();
        if (args != null) {
            eventId = args.getString(CommentsFragment.ARG_EVENT_ID);
        }

        recyclerView = view.findViewById(R.id.recycler_comments);
        inputContainer = view.findViewById(R.id.input_container);
        input = view.findViewById(R.id.et_comment);
        send = view.findViewById(R.id.btn_send);
        tvEmpty = view.findViewById(R.id.tv_empty);


        tvEmpty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);

        // start adapter
        adapter = new CommentAdapter(comments);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        loadComments();

        // get current user info
        UserLocalDataSource localDataSource = new UserLocalDataSource(requireContext());
        UserRemoteDataSource remoteDataSource = new UserRemoteDataSource(FirebaseFirestore.getInstance());
        UserRepository userRepository = new UserRepository(localDataSource, remoteDataSource);

        userRepository.getProfile(new UserRepository.UserCallback() {
            @Override
            public void onResult(User user) {
                // Use Firebase Auth as the source of truth — if no authenticated
                // (non-anonymous) Firebase user exists, treat as guest regardless of
                // any device UUID profile that may linger in Firestore after logout.
                com.google.firebase.auth.FirebaseUser fbUser =
                        com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                boolean isGuest = fbUser == null || fbUser.isAnonymous();

                if (!isGuest) {
                    currentUser = user;
                    determineCanDelete();
                    inputContainer.setVisibility(android.view.View.VISIBLE);
                } else {
                    currentUser = null;
                    inputContainer.setVisibility(android.view.View.GONE);
                }
            }
        });

        send.setOnClickListener(v -> {
            String text = input.getText().toString().trim();
            if (!text.isEmpty()) {
                addComment(text);
                input.setText("");
            }
        });

        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                String text = input.getText().toString().trim();
                if (!text.isEmpty()) {
                    addComment(text);
                    input.setText("");
                }
                return true;
            }
            return false;
        });
    }

    /**
     * Gets comments from the database through CommentRepository, sorts by date, and passes to CommentAdapter.
     */
    private void loadComments() {
        repo.getComments(eventId, result -> {
            comments.clear();
            if (result != null) {
                comments.addAll(result);
            }
            tvEmpty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);
            Collections.sort(comments, Collections.reverseOrder());
            adapter.notifyDataSetChanged();
        });
    }

    /**
     * Adds a comment and refreshes the adapter.
     * @param content
     */
    private void addComment(String content) {
        Log.d("mytag", "addComment: " + currentUser.getName());
        repo.addComment(eventId, currentUser.getUid(), currentUser.getName(), content, error -> {
            if (error == null) {
                loadComments(); // refresh
            } else {
                Toast.makeText(getContext(),"Error: Failed to post comment.", Toast.LENGTH_LONG);
            }
        });
    }

    /**
     * Determines if the user is allowed to delete comments for this event based on their role and
     * communicates with adapter to display delete button.
     */
    private void determineCanDelete() {
        if (currentUser == null) {
            canDelete = false;
            return;
        }
        // event organizer data needed
        EventRepository eventRepository = new EventRepository();
        // in case user is an admin
        String role = ((MainActivity) requireActivity()).getEffectiveRole();
        Log.d("mytag", "role: " + role);
        eventRepository.getEventByIdAsync(eventId, new EventRepository.EventCallback() {
            @Override
            public void onResult(Event event) {
                // get user role to determine delete capabilities
                // these states should probably be replaced by constants at some point...
                if (role.equals("admin")) {
                    canDelete = true;
                    adapter.setCanDelete(canDelete);
                } else if (role.equals("organizer")) {
                    Log.d("mytag", "event org id: " + event.getOrganizerId());
                    Log.d("mytag", "user id: " + currentUser.getUid());
                    // check if user is organizer of this event
                    if (event.getOrganizerId().equals(currentUser.getUid())) {
                        canDelete = true;
                        adapter.setCanDelete(canDelete);
                    }
                }
                /**else if (currentUser.getRole().equals("entrant")) {
                    // entrants do not have delete capabilities except for in the special case that they are a co-organizer
                    if (event.get...) {
                        canDelete = true;
                        adapter.setCanDelete(canDelete);
                    }
                }
                 **/
            }
        });
    }
}