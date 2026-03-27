package com.example.abacus_app;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller class that manages all actions related to the comments of all events.
 *
 * <p>
 *  NOTE: Methods in this class run ASYNCHRONOUSLY and require a callback. Only to be used from UI
 *  classes. For synchronous methods for the architecture layer (repositories), refer to
 *  {@link CommentRemoteDataSource}.
 *  </p>
 */
public class CommentRepository {

    private final CommentRemoteDataSource remoteDataSource;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final android.os.Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * Constructs the CommentRepository object.
     */
    CommentRepository() {
        remoteDataSource = new CommentRemoteDataSource();
    }

    /**
     * Adds a comment to a specific event.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId the unique ID of the user in the database
     * @param content the content of the comment
     * @param callback callback called when the operation completes
     */
    public void addComment(String eventId, String userId, String content, VoidCallback callback) {
        executor.submit(() -> {
           try {
               Log.d("mytagcommentrepo", "addComment: got to comment repo");
               remoteDataSource.addCommentSync(eventId, userId, content);
               Log.d("mytagcommentrepo", "addComment: finished addcommentSync");
               mainHandler.post(() -> callback.onComplete(null));
           } catch (Exception e) {
               mainHandler.post(() -> callback.onComplete(e));
           }
        });
    }

    /**
     * Gets all comments on a specific event.
     *
     * @param eventId the unique ID of the event in the database
     * @param callback callback called when the operation completes
     */
    public void getComments(String eventId, CommentsCallback callback) {
        executor.submit(() -> {
            try {
                ArrayList<Comment> comments = remoteDataSource.getCommentsSync(eventId);
                mainHandler.post(() -> callback.onResult(comments));
            } catch (Exception e) {
                mainHandler.post(() -> callback.onResult(null));
            }
        });
    }

    /**
     * Callback interface for void methods.
     */
    public interface VoidCallback {
        void onComplete(Exception error);
    }

    /**
     * Callback interface for methods returning an ArrayList of Comments.
     */
    public interface CommentsCallback {
        void onResult(ArrayList<Comment> comments);
    }
}
