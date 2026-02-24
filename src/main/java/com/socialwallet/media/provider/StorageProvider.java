package com.socialwallet.media.provider;

import java.io.InputStream;
import java.time.Duration;

/**
 * Abstraction over object storage (S3, MinIO, DigitalOcean Spaces).
 * Swap implementations by providing a different bean — no changes to service layer.
 */
public interface StorageProvider {

    /**
     * Upload a file to storage.
     *
     * @param inputStream   file content stream
     * @param storagePath   destination path in the bucket (e.g., "avatars/userId/mediaId.jpg")
     * @param contentType   MIME type
     * @param contentLength file size in bytes
     */
    void upload(InputStream inputStream, String storagePath, String contentType, long contentLength);

    /**
     * Delete a file from storage.
     *
     * @param storagePath path of the file to delete
     */
    void delete(String storagePath);

    /**
     * Generate a presigned download URL for direct file access (non-image files).
     *
     * @param storagePath path of the file
     * @param expiration  URL validity duration
     * @return presigned URL string
     */
    String generatePresignedUrl(String storagePath, Duration expiration);
}
