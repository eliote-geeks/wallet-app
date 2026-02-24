package com.socialwallet.notifications.dto;

import com.socialwallet.notifications.model.DeviceToken;
import com.socialwallet.notifications.model.Platform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceTokenDto {

    private UUID deviceId;
    private Platform platform;
    private Instant registeredAt;
    private boolean active;

    public static DeviceTokenDto from(DeviceToken entity) {
        return DeviceTokenDto.builder()
                .deviceId(entity.getId())
                .platform(entity.getPlatform())
                .registeredAt(entity.getRegisteredAt())
                .active(entity.isActive())
                .build();
    }
}
