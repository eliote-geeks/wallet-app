package com.socialwallet.notifications.dto;

import com.socialwallet.notifications.model.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterDeviceRequest {

    @NotNull(message = "Platform is required")
    private Platform platform;

    @NotBlank(message = "Token is required")
    private String token;
}
