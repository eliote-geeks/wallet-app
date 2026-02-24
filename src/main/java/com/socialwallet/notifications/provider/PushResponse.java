package com.socialwallet.notifications.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushResponse {
    private boolean success;
    private String errorMessage;
    private List<String> invalidTokens;
}
