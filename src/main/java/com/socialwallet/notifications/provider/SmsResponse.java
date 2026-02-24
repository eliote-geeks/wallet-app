package com.socialwallet.notifications.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsResponse {
    private boolean success;
    private String errorMessage;
    private String messageId;
}
