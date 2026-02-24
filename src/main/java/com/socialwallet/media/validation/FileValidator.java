package com.socialwallet.media.validation;

import com.socialwallet.media.config.MediaPolicyProperties;
import com.socialwallet.media.exception.InvalidMediaException;
import com.socialwallet.media.model.MediaPurpose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Validates uploaded files against purpose-specific policies.
 * Uses Apache Tika to detect the real MIME type from file content (magic bytes),
 * not trusting the Content-Type header or file extension.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FileValidator {

    private final MediaPolicyProperties policyProperties;
    private final Tika tika = new Tika();

    /**
     * Validate a file against the policy for the given purpose.
     *
     * @param file    the uploaded file
     * @param purpose the intended use of the file
     * @throws InvalidMediaException if validation fails
     */
    public void validate(MultipartFile file, MediaPurpose purpose) {
        if (file == null || file.isEmpty()) {
            throw new InvalidMediaException("File is empty or missing");
        }

        MediaPolicyProperties.PolicyEntry policy = policyProperties.getPolicyFor(purpose);

        // 1. Validate size
        validateSize(file, policy);

        // 2. Detect real MIME type and validate format
        String detectedMimeType = detectMimeType(file);
        validateFormat(detectedMimeType, policy, purpose);
    }

    /**
     * Detect the real MIME type using Apache Tika (reads magic bytes).
     * Returns the detected type, which may differ from the declared Content-Type.
     */
    public String detectMimeType(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            String detected = tika.detect(is, file.getOriginalFilename());
            log.debug("MIME detection: declared={}, detected={}, filename={}",
                    file.getContentType(), detected, file.getOriginalFilename());
            return detected;
        } catch (IOException e) {
            throw new InvalidMediaException("Failed to read file for MIME type detection");
        }
    }

    private void validateSize(MultipartFile file, MediaPolicyProperties.PolicyEntry policy) {
        long maxBytes = policy.getMaxSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new InvalidMediaException(
                    String.format("File size %d bytes exceeds maximum %d bytes (%d MB) for this media type",
                            file.getSize(), maxBytes, policy.getMaxSizeMb()));
        }
    }

    private void validateFormat(String detectedMimeType, MediaPolicyProperties.PolicyEntry policy, MediaPurpose purpose) {
        List<String> allowedTypes = policy.getAllowedTypes();

        if (!allowedTypes.contains(detectedMimeType)) {
            throw new InvalidMediaException(
                    String.format("File type '%s' is not allowed for %s. Allowed types: %s",
                            detectedMimeType, purpose, String.join(", ", allowedTypes)));
        }
    }
}
