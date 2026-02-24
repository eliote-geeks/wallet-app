package com.socialwallet.notifications.event.chat;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class MessageSentEvent extends BaseNotificationEvent {
    private UUID senderId;
    private UUID recipientId;
    private String senderName;
    private String messagePreview;
    private UUID conversationId;
}
