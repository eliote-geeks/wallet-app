package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.stories.StoryViewedEvent;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoryEventListener {

    private final NotificationService notificationService;
    private final PrivacyChecker privacyChecker;

    @Async("notificationExecutor")
    @EventListener
    public void onStoryViewed(StoryViewedEvent event) {
        try {
            if (!privacyChecker.canViewStory(event.getViewerId(), event.getAuthorId())) {
                log.debug("Story view notification skipped: privacy check failed for viewer={}, author={}",
                        event.getViewerId(), event.getAuthorId());
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("viewerName", event.getViewerName());
            data.put("storyId", event.getStoryId().toString());

            notificationService.send(event.getAuthorId(), NotificationType.STORY_VIEWED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process StoryViewedEvent: {}", e.getMessage(), e);
        }
    }
}
