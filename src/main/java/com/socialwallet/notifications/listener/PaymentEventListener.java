package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.payments.*;
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
public class PaymentEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onDepositConfirmed(DepositConfirmedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("newBalance", event.getNewBalance().toPlainString());
            notificationService.send(event.getUserId(), NotificationType.DEPOSIT_CONFIRMED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process DepositConfirmedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onDepositFailed(DepositFailedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("reason", event.getReason());
            notificationService.send(event.getUserId(), NotificationType.DEPOSIT_FAILED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process DepositFailedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onPayoutCompleted(PayoutCompletedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("destination", event.getDestination());
            notificationService.send(event.getUserId(), NotificationType.PAYOUT_COMPLETED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process PayoutCompletedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onPayoutFailed(PayoutFailedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("reason", event.getReason());
            notificationService.send(event.getUserId(), NotificationType.PAYOUT_FAILED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process PayoutFailedEvent: {}", e.getMessage(), e);
        }
    }
}
