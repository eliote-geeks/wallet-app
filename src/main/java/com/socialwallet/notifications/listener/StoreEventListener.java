package com.socialwallet.notifications.listener;

import com.socialwallet.notifications.event.store.ProductSoldEvent;
import com.socialwallet.notifications.event.store.PurchaseCompletedEvent;
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
public class StoreEventListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @EventListener
    public void onPurchaseCompleted(PurchaseCompletedEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("productTitle", event.getProductTitle());
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            data.put("purchaseId", event.getPurchaseId().toString());
            notificationService.send(event.getBuyerId(), NotificationType.PURCHASE_COMPLETED, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process PurchaseCompletedEvent: {}", e.getMessage(), e);
        }
    }

    @Async("notificationExecutor")
    @EventListener
    public void onProductSold(ProductSoldEvent event) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("buyerName", event.getBuyerName());
            data.put("productTitle", event.getProductTitle());
            data.put("amount", event.getAmount().toPlainString());
            data.put("currency", event.getCurrency());
            notificationService.send(event.getSellerId(), NotificationType.PRODUCT_SOLD, data, event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process ProductSoldEvent: {}", e.getMessage(), e);
        }
    }
}
