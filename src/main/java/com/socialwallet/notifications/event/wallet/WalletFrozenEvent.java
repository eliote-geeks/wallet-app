package com.socialwallet.notifications.event.wallet;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class WalletFrozenEvent extends BaseNotificationEvent {
    private UUID userId;
    private String reason;
}
