package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.chat.CallInitiatedEvent;
import com.socialwallet.notifications.event.chat.CallMissedEvent;
import com.socialwallet.notifications.event.chat.MessageSentEvent;
import com.socialwallet.notifications.model.NotificationType;
import com.socialwallet.notifications.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Listens to Chat module events and triggers notifications.
 * All listeners are async to avoid blocking the publishing transaction.
 * All exceptions are caught to prevent event bus disruption.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatEventListener {

    private final NotificationService notificationService;
    private final PrivacyChecker privacyChecker;

    @Async("notificationExecutor")
    @EventListener
    public void onMessageSent(MessageSentEvent event) {
        try {
            if (privacyChecker.isBlocked(event.getRecipientId(), event.getSenderId())) {
                log.debug("Message notification skipped: sender {} blocked by {}", event.getSenderId(), event.getRecipientId());
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("senderName", event.getSenderName());
            data.put("messagePreview", event.getMessagePreview());
            data.put("conversationId", event.getConversationId().toString());
            data.put("senderId", event.getSenderId().toString());

            notificationService.send(event.getRecipientId(), NotificationType.MESSAGE_RECEIVED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process MessageSentEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onCallInitiated(CallInitiatedEvent event) {
        try {
            if (privacyChecker.isBlocked(event.getCalleeId(), event.getCallerId())) {
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("callerName", event.getCallerName());
            data.put("callId", event.getCallId().toString());

            notificationService.send(event.getCalleeId(), NotificationType.CALL_INCOMING, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process CallInitiatedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onCallMissed(CallMissedEvent event) {
        try {
            if (privacyChecker.isBlocked(event.getCalleeId(), event.getCallerId())) {
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("callerName", event.getCallerName());

            notificationService.send(event.getCalleeId(), NotificationType.CALL_MISSED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process CallMissedEvent: {}", e.getMessage(), e);
        }
    }
}
