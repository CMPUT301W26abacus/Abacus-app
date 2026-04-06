package com.example.abacus_app;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * StorageRepository.java
 *
 * Replaced FirebaseStorage with Cloudinary to bypass Spark plan bucket limitations.
 * Responsible for uploading images (posters, profile photos, QR codes) and returning their public URLs.
 */
public class StorageRepository {

    private static final String TAG = "StorageRepository";
    private static final String UPLOAD_PRESET = "ml_default";

    public interface CloudinaryCallback {
        void onSuccess(String url);
        void onError(String error);
    }

    public StorageRepository() {
        // MediaManager is initialized in AbacusApplication
    }

    /**
     * Uploads an image from a Uri to Cloudinary.
     */
    public void uploadImage(Uri fileUri, CloudinaryCallback callback) {
        if (fileUri == null) {
            callback.onError("File URI is null");
            return;
        }

        MediaManager.get().upload(fileUri)
                .unsigned(UPLOAD_PRESET)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String publicUrl = (String) resultData.get("secure_url");
                        callback.onSuccess(publicUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        callback.onError(error.getDescription());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    /**
     * Uploads a Bitmap (e.g., QR code) to Cloudinary.
     * Converts Bitmap to a temporary file first as MediaManager.upload(byte[]) 
     * is not available in the unsigned Android SDK.
     */
    public void uploadQRCode(android.content.Context context, String eventId, Bitmap bitmap, CloudinaryCallback callback) {
        File tempFile = new File(context.getCacheDir(), "qr_" + eventId + ".png");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        } catch (IOException e) {
            callback.onError("Failed to save QR bitmap: " + e.getMessage());
            return;
        }

        MediaManager.get().upload(tempFile.getAbsolutePath())
                .unsigned(UPLOAD_PRESET)
                .callback(new UploadCallback() {
                    @Override public void onStart(String requestId) {}
                    @Override public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String publicUrl = (String) resultData.get("secure_url");
                        tempFile.delete();
                        callback.onSuccess(publicUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        tempFile.delete();
                        callback.onError(error.getDescription());
                    }

                    @Override public void onReschedule(String requestId, ErrorInfo error) {}
                }).dispatch();
    }

    /**
     * Helper for profile photo uploads.
     */
    public void uploadProfilePhoto(String uuid, Uri fileUri, CloudinaryCallback callback) {
        uploadImage(fileUri, callback);
    }

    /**
     * Helper for poster uploads.
     */
    public void uploadPoster(String eventId, Uri fileUri, CloudinaryCallback callback) {
        uploadImage(fileUri, callback);
    }

    /**
     * These methods are no longer applicable with Cloudinary's dynamic URLs,
     * but we keep the signatures (empty/null) if they are referenced elsewhere.
     * In Cloudinary, the URL is obtained immediately upon successful upload.
     */
    public String getPosterUrl(String eventId) { return null; }
    public String getQRCodeUrl(String eventId) { return null; }
    public String getProfilePhotoUrl(String uuid) { return null; }

    /**
     * Deletion in Cloudinary requires a signed request or Admin API,
     * which is not typically done from the client app.
     */
    public void deleteImage(String path) {
        Log.w(TAG, "Delete requested for " + path + ". Client-side delete not implemented for Cloudinary.");
    }
}
