package com.example.abacus_app;

import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
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
    private TextView tvEmpty;

    private List<Comment> comments = new ArrayList<>();
    private CommentAdapter adapter;

    private CommentRepository repo = new CommentRepository();

    private String eventId = null;
    private User currentUser  = null;

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
        input = view.findViewById(R.id.et_comment);
        send = view.findViewById(R.id.btn_send);
        tvEmpty = view.findViewById(R.id.tv_empty);


        tvEmpty.setVisibility(comments.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);

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
                currentUser = user;
                if (user == null) {
                    Log.d("mytag", "onResult: null :(");
                }
                if (user.getName() == null) {
                    Log.d("mytag", "onResult: null :(");
                } else {
                    Log.d("mytag", "onResult: " + user.getName());
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
}