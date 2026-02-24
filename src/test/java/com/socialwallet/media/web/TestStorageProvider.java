package com.socialwallet.media.web;

import com.socialwallet.media.provider.StorageProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;

/**
 * No-op storage provider for integration tests.
 * Replaces S3StorageProvider — no real S3 calls.
 */
@Component
@Primary
public class TestStorageProvider implements StorageProvider {

    @Override
    public void upload(InputStream inputStream, String storagePath, String contentType, long contentLength) {
        // No-op — skip S3 upload in tests
    }

    @Override
    public void delete(String storagePath) {
        // No-op
    }

    @Override
    public String generatePresignedUrl(String storagePath, Duration expiration) {
        return "https://test-cdn.local/" + storagePath;
    }
}