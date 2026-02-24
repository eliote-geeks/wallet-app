package com.socialwallet.notifications.dto;

import com.socialwallet.notifications.model.Notification;
import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationStatus;
import com.socialwallet.notifications.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {

    private UUID notificationId;
    private NotificationType type;
    private String title;
    private String body;
    private NotificationChannel channel;
    private NotificationStatus status;
    private boolean read;
    private Instant sentAt;
    private String deepLink;
    private Map<String, Object> data;

    public static NotificationDto from(Notification entity) {
        return NotificationDto.builder()
                .notificationId(entity.getId())
                .type(entity.getType())
                .title(entity.getTitle())
                .body(entity.getBody())
                .channel(entity.getChannel())
                .status(entity.getStatus())
                .read(entity.getReadAt() != null)
                .sentAt(entity.getSentAt())
                .deepLink(entity.getDeepLink())
                .data(entity.getData())
                .build();
    }
}
