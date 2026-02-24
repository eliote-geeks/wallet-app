package com.socialwallet.notifications.event.payments;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class DepositConfirmedEvent extends BaseNotificationEvent {
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private BigDecimal newBalance;
}
