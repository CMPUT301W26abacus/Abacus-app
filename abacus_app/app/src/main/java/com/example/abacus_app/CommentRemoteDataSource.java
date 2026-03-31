package com.example.abacus_app;

import static androidx.core.content.ContentProviderCompat.requireContext;

import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Performs CRUD operations on the comments data in remote Firestore database.
 * Maps data from the 'comments' collection under a specific event.
 *
 * NOTE: The methods in this class run SYNCHRONOUSLY and are only to be used in the architecture
 * layer (repositories). For methods related to UI, refer to {@link CommentRepository}.
 *
 * @author Kaylee
 */
public class CommentRemoteDataSource {

    private final FirebaseFirestore firestore;

    /**
     * Constructs a CommentRemoteDataSource object which can be used to read/write to the
     * comments collection of an event in the Firestore database.
     *
     * NOTE: This class should only be utilized from repository classes.
     */
    public CommentRemoteDataSource() {
        firestore = FirebaseFirestore.getInstance();
    }

    private CollectionReference getCollectionRef(String eventId) {
        return firestore
                .collection("events")
                .document(eventId)
                .collection("comments");
    }

    private Comment mapDocument(DocumentSnapshot doc) {
        if (!doc.exists()) return null;

        String commentId = doc.getString("commentId");
        String userId = doc.getString("userId");
        String username = doc.getString("username");
        String eventId = doc.getString("eventId");
        String content = doc.getString("content");
        Long timestamp = doc.getLong("timestamp");

        return new Comment(commentId, userId, username, eventId, content, timestamp);
    }

    /**
     * Adds a comment to a specific event.
     *
     * @param eventId the unique ID of the event in the database
     * @param userId the unique ID of the user in the database
     * @param content the content of the comment
     * @throws Exception something went wrong
     */
    public void addCommentSync(String eventId, String userId, String username, String content) throws Exception {

        DocumentReference docRef = getCollectionRef(eventId).document();
        Log.d("mytagCommentRDS", "addCommentSync: " + username);
        Comment comment = new Comment(docRef.getId(), userId, username, eventId, content, System.currentTimeMillis());
        Log.d("mytagCommentRDS", "addCommentSync: " + comment.getUsername());
        Log.d("mytagCommentRDS", "addCommentSync: before");
        Tasks.await(docRef.set(comment));
        Log.d("mytagCommentRDS", "addCommentSync: after");
    }

    /**
     * Returns all the comments for a specific event.
     *
     * @param eventId the unique ID of the event in the database
     * @return list of all Comment objects
     * @throws Exception something went wrong
     */
    public ArrayList<Comment> getCommentsSync(String eventId) throws Exception {
        QuerySnapshot snapshot = Tasks.await(getCollectionRef(eventId).get());

        ArrayList<Comment> comments = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Comment comment = mapDocument(doc);
            comments.add(comment);
        }
        return comments;
    }

    /**
     * Deletes a specific comment from the db by removing the document.
     *
     * @param eventId the unique ID of the event in the database
     * @param commentId the unique ID of the comment in the database
     * @throws Exception something went wrong
     */
    public void deleteCommentSync(String eventId, String commentId) throws Exception {
        DocumentReference docRef = getCollectionRef(eventId).document(commentId);
        Tasks.await(docRef.delete());
    }
}
