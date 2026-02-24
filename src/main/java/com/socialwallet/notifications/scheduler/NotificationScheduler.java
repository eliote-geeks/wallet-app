package com.socialwallet.notifications.scheduler;

import com.socialwallet.notifications.repository.DeviceTokenRepository;
import com.socialwallet.notifications.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Scheduled jobs for notification maintenance.
 * - Token Cleanup: Daily at 3 AM — deactivates tokens unused for 90 days
 * - Notification Purge: Daily at 4 AM — deletes records older than 90 days
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

    private static final int RETENTION_DAYS = 90;

    private final DeviceTokenRepository deviceTokenRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 3 * * *") // Daily at 3:00 AM
    @Transactional
    public void cleanupInactiveTokens() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deactivated = deviceTokenRepository.deactivateUnusedTokens(cutoff);
        if (deactivated > 0) {
            log.info("Token cleanup: deactivated {} unused device tokens (cutoff={})", deactivated, cutoff);
        }
    }

    @Scheduled(cron = "0 0 4 * * *") // Daily at 4:00 AM
    @Transactional
    public void purgeOldNotifications() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = notificationRepository.deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Notification purge: deleted {} old notifications (cutoff={})", deleted, cutoff);
        }
    }
}
