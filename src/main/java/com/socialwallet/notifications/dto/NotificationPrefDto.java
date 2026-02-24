package com.socialwallet.notifications.dto;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationPreference;
import com.socialwallet.notifications.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefDto {

    private NotificationType notificationType;
    private boolean enabled;
    private List<NotificationChannel> channels;

    public static NotificationPrefDto from(NotificationPreference entity) {
        return NotificationPrefDto.builder()
                .notificationType(entity.getNotificationType())
                .enabled(entity.isEnabled())
                .channels(entity.getChannels())
                .build();
    }
}
