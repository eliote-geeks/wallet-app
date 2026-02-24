package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.compliance.KYCApprovedEvent;
import com.socialwallet.notifications.event.compliance.KYCRejectedEvent;
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
public class ComplianceEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onKYCApproved(KYCApprovedEvent event) {
        try {
            notificationService.send(event.getUserId(), NotificationType.KYC_APPROVED, new HashMap<>(), event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process KYCApprovedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onKYCRejected(KYCRejectedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", event.getReason());
            notificationService.send(event.getUserId(), NotificationType.KYC_REJECTED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process KYCRejectedEvent: {}", e.getMessage(), e);
        }
    }
}
