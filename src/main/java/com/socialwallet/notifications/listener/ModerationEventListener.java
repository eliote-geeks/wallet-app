package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.moderation.*;
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
public class ModerationEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onUserBanned(UserBannedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", event.getReason());
            notificationService.send(event.getUserId(), NotificationType.USER_BANNED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process UserBannedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onBanAppealResolved(BanAppealResolvedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("resolution", event.getResolution());
            notificationService.send(event.getUserId(), NotificationType.BAN_APPEAL_RESOLVED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process BanAppealResolvedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onModerationWarning(ModerationWarningEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", event.getReason());
            notificationService.send(event.getUserId(), NotificationType.MODERATION_WARNING, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process ModerationWarningEvent: {}", e.getMessage(), e);
        }
    }
}
