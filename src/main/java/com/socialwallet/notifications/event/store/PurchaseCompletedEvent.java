package com.socialwallet.notifications.event.store;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class PurchaseCompletedEvent extends BaseNotificationEvent {
    private UUID buyerId;
    private UUID sellerId;
    private String productTitle;
    private BigDecimal amount;
    private String currency;
    private UUID purchaseId;
}
