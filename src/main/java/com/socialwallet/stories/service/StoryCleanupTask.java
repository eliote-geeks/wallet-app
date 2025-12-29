package com.socialwallet.stories.service;

import com.socialwallet.stories.repository.StoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Scheduled task to automatically delete expired stories.
 * Runs every hour.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StoryCleanupTask {

    private final StoryRepository storyRepository;

    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    @Transactional
    public void cleanupExpiredStories() {
        log.info("Starting cleanup of expired stories...");
        
        LocalDateTime now = LocalDateTime.now();
        long countBefore = storyRepository.count();
        
        storyRepository.deleteByExpiresAtBefore(now);
        
        long countAfter = storyRepository.count();
        long deleted = countBefore - countAfter;
        
        if (deleted > 0) {
            log.info("Cleanup completed: {} expired stories deleted", deleted);
        } else {
            log.debug("Cleanup completed: no expired stories found");
        }
    }
}