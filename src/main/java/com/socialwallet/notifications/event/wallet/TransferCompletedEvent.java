package com.socialwallet.notifications.event.wallet;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.math.BigDecimal;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class TransferCompletedEvent extends BaseNotificationEvent {
    private UUID senderId;
    private UUID recipientId;
    private String senderName;
    private BigDecimal amount;
    private String currency;
    private BigDecimal newBalance;
    private UUID transferId;
}
