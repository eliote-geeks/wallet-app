package com.socialwallet.media.dto;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Signed URL for displaying or downloading a media asset.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaUrlDto {
    private UUID mediaId;
    private String url;
    private Instant expiresAt;
}
