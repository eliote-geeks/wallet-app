package com.socialwallet.notifications.event.chat;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class CallInitiatedEvent extends BaseNotificationEvent {
    private UUID callerId;
    private UUID calleeId;
    private String callerName;
    private UUID callId;
}
