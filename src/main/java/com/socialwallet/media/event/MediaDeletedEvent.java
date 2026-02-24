package com.socialwallet.media.event;

import com.socialwallet.media.model.MediaPurpose;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a media asset is soft-deleted.
 */
@Getter
@AllArgsConstructor
public class MediaDeletedEvent {
    private final UUID mediaId;
    private final UUID deletedBy;
    private final MediaPurpose purpose;
    private final Instant timestamp;

    public MediaDeletedEvent(UUID mediaId, UUID deletedBy, MediaPurpose purpose) {
        this(mediaId, deletedBy, purpose, Instant.now());
    }
}
