package com.socialwallet.media.dto;

import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a media asset (metadata only, no file content).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaDto {
    private UUID mediaId;
    private UUID uploaderId;
    private MediaPurpose purpose;
    private String originalFilename;
    private String mimeType;
    private Long sizeBytes;
    private MediaStatus status;
    private Instant createdAt;
    private Instant expiresAt;
}
