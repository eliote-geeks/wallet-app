package com.socialwallet.media.service;

import com.socialwallet.media.config.MediaPolicyProperties;
import com.socialwallet.media.event.MediaExpiredEvent;
import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.provider.StorageProvider;
import com.socialwallet.media.repository.MediaAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled cleanup tasks for media lifecycle management.
 * <p>
 * - Expired media cleanup: marks expired UPLOADED media as EXPIRED, daily at 2 AM.
 * - Soft-delete purge: physically deletes DELETED media after grace period, daily at 3 AM.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MediaCleanupTask {

    private final MediaAssetRepository mediaRepository;
    private final StorageProvider storageProvider;
    private final MediaPolicyProperties policyProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Mark expired media as EXPIRED.
     * Runs daily at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void cleanupExpiredMedia() {
        Instant now = Instant.now();
        int count = mediaRepository.markExpiredMedia(now);

        if (count > 0) {
            log.info("Marked {} expired media assets", count);

            // Publish events for each expired media
            List<MediaAsset> expired = mediaRepository.findExpiredMedia(now);
            for (MediaAsset asset : expired) {
                eventPublisher.publishEvent(new MediaExpiredEvent(asset.getId(), asset.getPurpose()));
            }
        }
    }

    /**
     * Physically delete soft-deleted media from S3 after the grace period.
     * Runs daily at 3 AM.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeSoftDeletedMedia() {
        Instant cutoff = Instant.now().minus(policyProperties.getSoftDeleteGraceDays(), ChronoUnit.DAYS);
        List<MediaAsset> toDelete = mediaRepository.findDeletedMediaOlderThan(cutoff);

        int deleted = 0;
        for (MediaAsset asset : toDelete) {
            try {
                storageProvider.delete(asset.getStoragePath());
                mediaRepository.delete(asset);
                deleted++;
            } catch (Exception e) {
                log.error("Failed to purge media {} from storage: {}", asset.getId(), e.getMessage());
                // Continue with next — failed ones will be retried on next run
            }
        }

        if (deleted > 0) {
            log.info("Purged {} soft-deleted media assets from storage", deleted);
        }
    }
}
