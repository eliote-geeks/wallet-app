package com.socialwallet.media.event;

import com.socialwallet.media.model.MediaPurpose;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a media file is successfully uploaded to storage.
 */
@Getter
@AllArgsConstructor
public class MediaUploadedEvent {
    private final UUID mediaId;
    private final UUID uploaderId;
    private final MediaPurpose purpose;
    private final String mimeType;
    private final Long sizeBytes;
    private final Instant timestamp;

    public MediaUploadedEvent(UUID mediaId, UUID uploaderId, MediaPurpose purpose, String mimeType, Long sizeBytes) {
        this(mediaId, uploaderId, purpose, mimeType, sizeBytes, Instant.now());
    }
}
