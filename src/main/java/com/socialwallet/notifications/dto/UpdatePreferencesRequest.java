package com.socialwallet.notifications.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreferencesRequest {

    @NotEmpty(message = "At least one preference must be provided")
    @Valid
    private List<NotificationPrefItemRequest> preferences;
}
