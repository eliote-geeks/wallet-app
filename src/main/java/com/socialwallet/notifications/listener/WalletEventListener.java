package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.wallet.TransferCompletedEvent;
import com.socialwallet.notifications.event.wallet.WalletFrozenEvent;
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
public class WalletEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onTransferCompleted(TransferCompletedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("senderName", event.getSenderName());
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("newBalance", event.getNewBalance().toPlainString());
            data.put("transferId", event.getTransferId().toString());

            notificationService.send(event.getRecipientId(), NotificationType.TIP_RECEIVED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process TransferCompletedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onWalletFrozen(WalletFrozenEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", event.getReason());

            notificationService.send(event.getUserId(), NotificationType.WALLET_FROZEN, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process WalletFrozenEvent: {}", e.getMessage(), e);
        }
    }
}
