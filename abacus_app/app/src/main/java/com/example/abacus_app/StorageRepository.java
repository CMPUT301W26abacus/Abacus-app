package com.example.abacus_app;

import android.graphics.Bitmap;
import android.net.Uri;
import com.google.android.gms.tasks.Task;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;

/**
 * Centralized data access layer for Firebase Storage media files.
 * Responsible for uploading and retrieving event posters and QR codes.
 * 
 * @author Himesh
 * @version 1.1
 */
public class StorageRepository {
    private final FirebaseStorage storage;
    private final StorageReference storageRef;

    /**
     * Initializes the Firebase Storage instance and root reference.
     */
    public StorageRepository() {
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
    }

    /**
     * Uploads an event poster image to Firebase Storage.
     * US 02.04.01: Upload event poster to event details page.
     * Path: posters/{eventId}.jpg
     * 
     * @param eventId The unique ID of the event.
     * @param fileUri The local Uri of the image to be uploaded.
     * @return An {@link UploadTask} for monitoring the upload progress.
     */
    public UploadTask uploadPoster(String eventId, Uri fileUri) {
        StorageReference posterRef = storageRef.child("posters/" + eventId + ".jpg");
        return posterRef.putFile(fileUri);
    }

    /**
     * Uploads a generated QR code image to Firebase Storage.
     * US 02.01.01: Generate unique promotional QR code.
     * Path: qrcodes/{eventId}.png
     * 
     * @param eventId The unique ID of the event.
     * @param bitmap  The {@link Bitmap} image of the QR code to be uploaded.
     * @return An {@link UploadTask} for monitoring the upload progress.
     */
    public UploadTask uploadQRCode(String eventId, Bitmap bitmap) {
        StorageReference qrRef = storageRef.child("qrcodes/" + eventId + ".png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();
        return qrRef.putBytes(data);
    }

    /**
     * Retrieves the download URL for a specific event's poster.
     * 
     * @param eventId The unique ID of the event.
     * @return A {@link Task} containing the {@link Uri} download URL.
     */
    public Task<Uri> getPosterUrl(String eventId) {
        return storageRef.child("posters/" + eventId + ".jpg").getDownloadUrl();
    }

    /**
     * Retrieves the download URL for a specific event's QR code.
     * 
     * @param eventId The unique ID of the event.
     * @return A {@link Task} containing the {@link Uri} download URL.
     */
    public Task<Uri> getQRCodeUrl(String eventId) {
        return storageRef.child("qrcodes/" + eventId + ".png").getDownloadUrl();
    }

    /**
     * Deletes a file from Firebase Storage.
     * US 03.03.01 context: Remove images.
     * 
     * @param path The full path of the file to delete (e.g., "posters/123.jpg").
     * @return A {@link Task} indicating the result of the deletion.
     */
    public Task<Void> deleteImage(String path) {
        return storageRef.child(path).delete();
    }
}
