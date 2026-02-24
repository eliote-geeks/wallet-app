package com.socialwallet.notifications.event.stories;
import com.socialwallet.notifications.event.BaseNotificationEvent;

import lombok.*;
import lombok.experimental.SuperBuilder;
import java.util.UUID;

@Data @SuperBuilder @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode(callSuper = true)
public class StoryViewedEvent extends BaseNotificationEvent {
    private UUID authorId;
    private UUID viewerId;
    private String viewerName;
    private UUID storyId;
}
