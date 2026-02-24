package com.socialwallet.media.event;

import com.socialwallet.media.model.MediaPurpose;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a media asset expires (past its retention date).
 */
@Getter
@AllArgsConstructor
public class MediaExpiredEvent {
    private final UUID mediaId;
    private final MediaPurpose purpose;
    private final Instant timestamp;

    public MediaExpiredEvent(UUID mediaId, MediaPurpose purpose) {
        this(mediaId, purpose, Instant.now());
    }
}
