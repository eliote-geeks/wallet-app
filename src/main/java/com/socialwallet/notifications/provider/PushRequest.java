package com.socialwallet.notifications.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushRequest {
    private List<String> deviceTokens;
    private String title;
    private String body;
    private Map<String, Object> data;
    private String deepLink;
}
