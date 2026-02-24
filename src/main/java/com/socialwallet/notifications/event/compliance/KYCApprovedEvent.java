package com.socialwallet.notifications.event.compliance;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class KYCApprovedEvent extends BaseNotificationEvent {
    private UUID userId;
}
