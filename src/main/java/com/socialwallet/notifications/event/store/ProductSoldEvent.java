package com.socialwallet.notifications.event.store;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class ProductSoldEvent extends BaseNotificationEvent {
    private UUID sellerId;
    private UUID buyerId;
    private String buyerName;
    private String productTitle;
    private BigDecimal amount;
    private String currency;
}
