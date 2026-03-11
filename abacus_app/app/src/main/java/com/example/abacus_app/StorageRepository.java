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
 * Owner: Himesh
 */
public class StorageRepository {
    private final FirebaseStorage storage;
    private final StorageReference storageRef;

    public StorageRepository() {
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
    }

    public UploadTask uploadPoster(String eventId, Uri fileUri) {
        StorageReference posterRef = storageRef.child("posters/" + eventId + ".jpg");
        return posterRef.putFile(fileUri);
    }

    public UploadTask uploadQRCode(String eventId, Bitmap bitmap) {
        StorageReference qrRef = storageRef.child("qrcodes/" + eventId + ".png");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos);
        byte[] data = baos.toByteArray();
        return qrRef.putBytes(data);
    }

    public Task<Uri> getPosterUrl(String eventId) {
        return storageRef.child("posters/" + eventId + ".jpg").getDownloadUrl();
    }

    public Task<Uri> getQRCodeUrl(String eventId) {
        return storageRef.child("qrcodes/" + eventId + ".png").getDownloadUrl();
    }

    public Task<Void> deleteImage(String path) {
        return storageRef.child(path).delete();
    }
}
