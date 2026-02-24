package com.socialwallet.notifications.event.identity;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class SuspiciousLoginEvent extends BaseNotificationEvent {
    private UUID userId;
    private String location;
    private String deviceName;
}
