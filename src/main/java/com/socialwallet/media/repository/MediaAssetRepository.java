package com.socialwallet.media.repository;

import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    /**
     * Find a media asset by its idempotency key and uploader.
     * Used to detect duplicate uploads.
     */
    Optional<MediaAsset> findByIdempotencyKeyAndUploaderId(String idempotencyKey, UUID uploaderId);

    /**
     * Find active (UPLOADED) media by ID.
     */
    Optional<MediaAsset> findByIdAndStatusNot(UUID id, MediaStatus status);

    /**
     * Count uploads in the last hour for rate limiting.
     */
    @Query("SELECT COUNT(m) FROM MediaAsset m WHERE m.uploaderId = :uploaderId AND m.createdAt > :since")
    long countRecentUploads(@Param("uploaderId") UUID uploaderId, @Param("since") Instant since);

    /**
     * Find all expired media that are still in UPLOADED status (for cleanup job).
     */
    @Query("SELECT m FROM MediaAsset m WHERE m.expiresAt IS NOT NULL AND m.expiresAt < :now AND m.status = 'UPLOADED'")
    List<MediaAsset> findExpiredMedia(@Param("now") Instant now);

    /**
     * Find all soft-deleted media older than the grace period (for physical deletion).
     */
    @Query("SELECT m FROM MediaAsset m WHERE m.status = 'DELETED' AND m.createdAt < :before")
    List<MediaAsset> findDeletedMediaOlderThan(@Param("before") Instant before);

    /**
     * Find all media for a given uploader and purpose (e.g., find current avatar).
     */
    List<MediaAsset> findByUploaderIdAndPurposeAndStatus(UUID uploaderId, MediaPurpose purpose, MediaStatus status);

    /**
     * Bulk update status for expired media.
     */
    @Modifying
    @Query("UPDATE MediaAsset m SET m.status = 'EXPIRED' WHERE m.expiresAt IS NOT NULL AND m.expiresAt < :now AND m.status = 'UPLOADED'")
    int markExpiredMedia(@Param("now") Instant now);
}
