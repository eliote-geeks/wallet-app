package com.socialwallet.notifications.dto;

import com.socialwallet.notifications.model.NotificationChannel;
import com.socialwallet.notifications.model.NotificationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPrefItemRequest {

    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;

    private boolean enabled;

    private List<NotificationChannel> channels;
}
