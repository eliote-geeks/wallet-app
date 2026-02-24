package com.socialwallet.media.provider;

import com.socialwallet.media.config.S3Properties;
import com.socialwallet.media.exception.MediaUploadException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Duration;

/**
 * S3-compatible storage provider.
 * Works with DigitalOcean Spaces, AWS S3, MinIO — any S3-compatible service.
 * To switch providers: change only the endpoint/credentials in S3Properties.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3StorageProvider implements StorageProvider {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public void upload(InputStream inputStream, String storagePath, String contentType, long contentLength) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storagePath)
                    .contentType(contentType)
                    .contentLength(contentLength)
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(inputStream, contentLength));
            log.info("Uploaded media to s3://{}/{}", s3Properties.getBucket(), storagePath);
        } catch (S3Exception e) {
            log.error("Failed to upload media to S3: {}", storagePath, e);
            throw new MediaUploadException("Failed to upload file to storage", e);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(s3Properties.getBucket())
                    .key(storagePath)
                    .build();

            s3Client.deleteObject(request);
            log.info("Deleted media from s3://{}/{}", s3Properties.getBucket(), storagePath);
        } catch (S3Exception e) {
            log.error("Failed to delete media from S3: {}", storagePath, e);
            // Don't throw — deletion failures are logged but not propagated.
            // The cleanup job will retry on the next run.
        }
    }

    @Override
    public String generatePresignedUrl(String storagePath, Duration expiration) {
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(expiration)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(s3Properties.getBucket())
                        .key(storagePath)
                        .build())
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }
}
