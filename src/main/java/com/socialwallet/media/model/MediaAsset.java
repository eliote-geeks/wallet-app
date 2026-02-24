package com.socialwallet.media.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Core entity representing a single uploaded media file.
 * Only the original file is stored — variants (thumbnails, resized) are
 * generated on the fly by imgproxy at request time.
 */
@Entity
@Table(name = "media_assets", indexes = {
        @Index(name = "idx_media_uploader", columnList = "uploader_id"),
        @Index(name = "idx_media_purpose", columnList = "purpose, status"),
        @Index(name = "idx_media_storage_path", columnList = "storage_path")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "uploader_id", nullable = false)
    private UUID uploaderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MediaPurpose purpose;

    @Column(name = "original_filename", length = 500)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MediaStatus status = MediaStatus.UPLOADED;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
