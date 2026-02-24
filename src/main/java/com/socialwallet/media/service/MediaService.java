package com.socialwallet.media.service;

import com.socialwallet.media.config.MediaPolicyProperties;
import com.socialwallet.media.dto.MediaDto;
import com.socialwallet.media.dto.MediaUrlDto;
import com.socialwallet.media.event.MediaDeletedEvent;
import com.socialwallet.media.event.MediaUploadedEvent;
import com.socialwallet.media.exception.*;
import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import com.socialwallet.media.provider.StorageProvider;
import com.socialwallet.media.repository.MediaAssetRepository;
import com.socialwallet.media.validation.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Core service for media upload, retrieval, and lifecycle management.
 * <p>
 * Public methods are called by the REST controller.
 * Internal methods (getDisplayUrl, getDownloadUrl, exists) are called directly
 * by other modules within the monolith (Profiles, Stories, Chat, Store).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MediaService {

    private final MediaAssetRepository mediaRepository;
    private final StorageProvider storageProvider;
    private final FileValidator fileValidator;
    private final UrlSigningService urlSigningService;
    private final MediaPolicyProperties policyProperties;
    private final ApplicationEventPublisher eventPublisher;

    /** MIME types that imgproxy can process (images only). */
    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    // ──────────────────────────────────────────────
    // PUBLIC API (called by MediaController)
    // ──────────────────────────────────────────────

    /**
     * Upload a media file.
     * Validates file, checks rate limit and idempotency, uploads to S3, persists metadata.
     */
    @Transactional
    public MediaDto upload(UUID uploaderId, MultipartFile file, MediaPurpose purpose, String idempotencyKey) {
        // 1. Idempotency check
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = mediaRepository.findByIdempotencyKeyAndUploaderId(idempotencyKey, uploaderId);
            if (existing.isPresent()) {
                log.info("Idempotent upload: returning existing media {}", existing.get().getId());
                return toDto(existing.get());
            }
        }

        // 2. Rate limit check
        checkRateLimit(uploaderId);

        // 3. Validate file (size, MIME type via magic bytes)
        fileValidator.validate(file, purpose);
        String detectedMimeType = fileValidator.detectMimeType(file);

        // 4. Build storage path
        UUID mediaId = UUID.randomUUID();
        String extension = resolveExtension(detectedMimeType, file.getOriginalFilename());
        String storagePath = buildStoragePath(purpose, uploaderId, mediaId, extension);

        // 5. Upload to S3
        MediaAsset asset = MediaAsset.builder()
                .id(mediaId)
                .uploaderId(uploaderId)
                .purpose(purpose)
                .originalFilename(file.getOriginalFilename())
                .mimeType(detectedMimeType)
                .sizeBytes(file.getSize())
                .storagePath(storagePath)
                .status(MediaStatus.UPLOADED)
                .idempotencyKey(idempotencyKey)
                .expiresAt(computeExpiration(purpose))
                .build();

        try {
            storageProvider.upload(file.getInputStream(), storagePath, detectedMimeType, file.getSize());
        } catch (IOException e) {
            log.error("Failed to read uploaded file stream", e);
            asset.setStatus(MediaStatus.FAILED);
            mediaRepository.save(asset);
            throw new MediaUploadException("Failed to read uploaded file", e);
        } catch (MediaUploadException e) {
            asset.setStatus(MediaStatus.FAILED);
            mediaRepository.save(asset);
            throw e;
        }

        // 6. Persist metadata
        asset = mediaRepository.save(asset);

        // 7. Handle avatar replacement (only one active avatar per user)
        if (purpose == MediaPurpose.AVATAR) {
            deactivatePreviousAvatars(uploaderId, mediaId);
        }

        // 8. Publish event
        eventPublisher.publishEvent(new MediaUploadedEvent(
                asset.getId(), uploaderId, purpose, detectedMimeType, file.getSize()));

        log.info("Media uploaded: id={}, purpose={}, size={}, mimeType={}",
                asset.getId(), purpose, file.getSize(), detectedMimeType);
        return toDto(asset);
    }

    /**
     * Get media metadata by ID. Returns metadata for any UPLOADED media.
     */
    @Transactional(readOnly = true)
    public MediaDto getMetadata(UUID mediaId) {
        MediaAsset asset = findActiveMedia(mediaId);
        return toDto(asset);
    }

    /**
     * Get a signed display URL for a media asset (REST endpoint).
     * For images: returns imgproxy URL with optional resize.
     * For non-images: returns presigned S3 URL.
     */
    @Transactional(readOnly = true)
    public MediaUrlDto getDisplayUrl(UUID mediaId, Integer width, Integer height, boolean crop) {
        MediaAsset asset = findActiveMedia(mediaId);

        String url;
        if (isImage(asset.getMimeType())) {
            if (width != null && width > 0) {
                int h = (height != null) ? height : 0;
                url = urlSigningService.buildImageDisplayUrl(asset.getStoragePath(), width, h, crop);
            } else {
                url = urlSigningService.buildImageDisplayUrl(asset.getStoragePath());
            }
        } else {
            url = urlSigningService.buildDownloadUrl(asset.getStoragePath());
        }

        return MediaUrlDto.builder()
                .mediaId(mediaId)
                .url(url)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    /**
     * Get a signed download URL (REST endpoint — uploader only).
     */
    @Transactional(readOnly = true)
    public MediaUrlDto getDownloadUrl(UUID requesterId, UUID mediaId) {
        MediaAsset asset = findActiveMedia(mediaId);

        if (!asset.getUploaderId().equals(requesterId)) {
            throw new MediaAccessDeniedException("Only the uploader can request a download URL via API");
        }

        String url = urlSigningService.buildDownloadUrl(asset.getStoragePath());
        return MediaUrlDto.builder()
                .mediaId(mediaId)
                .url(url)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();
    }

    /**
     * Soft-delete a media asset (REST endpoint — uploader only).
     */
    @Transactional
    public void delete(UUID requesterId, UUID mediaId) {
        MediaAsset asset = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));

        if (!asset.getUploaderId().equals(requesterId)) {
            throw new MediaAccessDeniedException("Only the uploader can delete this media");
        }

        if (asset.getStatus() == MediaStatus.DELETED) {
            return; // Idempotent
        }

        asset.setStatus(MediaStatus.DELETED);
        mediaRepository.save(asset);

        eventPublisher.publishEvent(new MediaDeletedEvent(mediaId, requesterId, asset.getPurpose()));
        log.info("Media soft-deleted: id={}, by={}", mediaId, requesterId);
    }

    // ──────────────────────────────────────────────
    // INTERNAL METHODS (called by other modules)
    // ──────────────────────────────────────────────

    /**
     * Get a signed display URL for an image with resize parameters.
     * Used by Profiles (avatar thumbnail), Chat (inline image), etc.
     */
    public String getDisplayUrl(UUID mediaId, int width, int height) {
        MediaAsset asset = findActiveMedia(mediaId);
        if (isImage(asset.getMimeType())) {
            return urlSigningService.buildImageDisplayUrl(asset.getStoragePath(), width, height, false);
        }
        return urlSigningService.buildDownloadUrl(asset.getStoragePath());
    }

    /**
     * Get a signed display URL at original size.
     * Used by Stories (full-size story view), Store (product image).
     */
    public String getDisplayUrl(UUID mediaId) {
        MediaAsset asset = findActiveMedia(mediaId);
        if (isImage(asset.getMimeType())) {
            return urlSigningService.buildImageDisplayUrl(asset.getStoragePath());
        }
        return urlSigningService.buildDownloadUrl(asset.getStoragePath());
    }

    /**
     * Get a signed download URL for a file.
     * Used by Store (deliver purchased product asset to buyer).
     */
    public String getDownloadUrl(UUID mediaId) {
        MediaAsset asset = findActiveMedia(mediaId);
        return urlSigningService.buildDownloadUrl(asset.getStoragePath());
    }

    /**
     * Check if a media asset exists and is active (UPLOADED).
     * Used by all modules to validate media references before persisting.
     */
    public boolean exists(UUID mediaId) {
        return mediaRepository.findById(mediaId)
                .map(asset -> asset.getStatus() == MediaStatus.UPLOADED)
                .orElse(false);
    }

    /**
     * Soft-delete all media for a specific purpose and uploader.
     * Used by Stories cleanup when a story is deleted.
     */
    @Transactional
    public void deleteByUploaderAndPurpose(UUID uploaderId, MediaPurpose purpose) {
        List<MediaAsset> assets = mediaRepository
                .findByUploaderIdAndPurposeAndStatus(uploaderId, purpose, MediaStatus.UPLOADED);

        for (MediaAsset asset : assets) {
            asset.setStatus(MediaStatus.DELETED);
            mediaRepository.save(asset);
            eventPublisher.publishEvent(new MediaDeletedEvent(asset.getId(), uploaderId, purpose));
        }

        if (!assets.isEmpty()) {
            log.info("Soft-deleted {} media assets for uploader={}, purpose={}", assets.size(), uploaderId, purpose);
        }
    }

    /**
     * Set the expiration date on a media asset.
     * Used when a story expires — media gets a 7-day grace period before physical deletion.
     */
    @Transactional
    public void setExpiration(UUID mediaId, Instant expiresAt) {
        mediaRepository.findById(mediaId).ifPresent(asset -> {
            asset.setExpiresAt(expiresAt);
            mediaRepository.save(asset);
            log.info("Set expiration for media {}: {}", mediaId, expiresAt);
        });
    }

    // ──────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────

    private MediaAsset findActiveMedia(UUID mediaId) {
        MediaAsset asset = mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Media not found: " + mediaId));

        if (asset.getStatus() != MediaStatus.UPLOADED) {
            throw new MediaNotFoundException("Media is no longer available: " + mediaId);
        }
        return asset;
    }

    private void checkRateLimit(UUID uploaderId) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        long recentCount = mediaRepository.countRecentUploads(uploaderId, oneHourAgo);

        if (recentCount >= policyProperties.getMaxUploadsPerHour()) {
            throw new RateLimitExceededException(
                    String.format("Upload rate limit exceeded: %d uploads in the last hour (max %d)",
                            recentCount, policyProperties.getMaxUploadsPerHour()));
        }
    }

    private void deactivatePreviousAvatars(UUID uploaderId, UUID currentMediaId) {
        List<MediaAsset> previous = mediaRepository
                .findByUploaderIdAndPurposeAndStatus(uploaderId, MediaPurpose.AVATAR, MediaStatus.UPLOADED);

        for (MediaAsset old : previous) {
            if (!old.getId().equals(currentMediaId)) {
                old.setStatus(MediaStatus.DELETED);
                mediaRepository.save(old);
                log.info("Deactivated previous avatar: {}", old.getId());
            }
        }
    }

    private boolean isImage(String mimeType) {
        return IMAGE_MIME_TYPES.contains(mimeType);
    }

    /**
     * Build an organized storage path: {purpose}/{uploaderId}/{mediaId}.{ext}
     */
    private String buildStoragePath(MediaPurpose purpose, UUID uploaderId, UUID mediaId, String extension) {
        String folder = purpose.name().toLowerCase().replace("_", "-");
        return String.format("%s/%s/%s%s", folder, uploaderId, mediaId, extension);
    }

    /**
     * Resolve file extension from detected MIME type, fallback to original filename extension.
     */
    private String resolveExtension(String mimeType, String originalFilename) {
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "audio/mpeg" -> ".mp3";
            case "audio/ogg" -> ".ogg";
            case "application/pdf" -> ".pdf";
            case "application/zip" -> ".zip";
            default -> extractExtension(originalFilename);
        };
    }

    private String extractExtension(String filename) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.'));
        }
        return "";
    }

    private Instant computeExpiration(MediaPurpose purpose) {
        MediaPolicyProperties.PolicyEntry policy = policyProperties.getPolicyFor(purpose);
        if (policy.getExpirationDays() != null) {
            return Instant.now().plus(policy.getExpirationDays(), ChronoUnit.DAYS);
        }
        return null; // No expiration (permanent)
    }

    private MediaDto toDto(MediaAsset asset) {
        return MediaDto.builder()
                .mediaId(asset.getId())
                .uploaderId(asset.getUploaderId())
                .purpose(asset.getPurpose())
                .originalFilename(asset.getOriginalFilename())
                .mimeType(asset.getMimeType())
                .sizeBytes(asset.getSizeBytes())
                .status(asset.getStatus())
                .createdAt(asset.getCreatedAt())
                .expiresAt(asset.getExpiresAt())
                .build();
    }
}
