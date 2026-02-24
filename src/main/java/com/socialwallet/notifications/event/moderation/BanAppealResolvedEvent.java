package com.socialwallet.notifications.event.moderation;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class BanAppealResolvedEvent extends BaseNotificationEvent {
    private UUID userId;
    private String resolution;
}
