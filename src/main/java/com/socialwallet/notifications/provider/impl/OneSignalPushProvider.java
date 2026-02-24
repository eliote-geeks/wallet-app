package com.socialwallet.notifications.provider.impl;

import com.socialwallet.notifications.provider.PushProvider;
import com.socialwallet.notifications.provider.PushRequest;
import com.socialwallet.notifications.provider.PushResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * OneSignal Push API implementation.
 * Free tier: unlimited push notifications.
 */
@Component
public class OneSignalPushProvider implements PushProvider {

    private static final Logger log = LoggerFactory.getLogger(OneSignalPushProvider.class);
    private static final String ONESIGNAL_API_URL = "https://onesignal.com/api/v1/notifications";

    private final RestClient restClient;
    private final String appId;

    public OneSignalPushProvider(
            @Value("${notifications.onesignal.app-id:}") String appId,
            @Value("${notifications.onesignal.api-key:}") String apiKey) {
        this.appId = appId;
        this.restClient = RestClient.builder()
                .baseUrl(ONESIGNAL_API_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + apiKey)
                .build();
    }

    @Override
    public PushResponse send(PushRequest request) {
        if (request.getDeviceTokens() == null || request.getDeviceTokens().isEmpty()) {
            return PushResponse.builder().success(false).errorMessage("No device tokens provided").build();
        }

        if (appId == null || appId.isBlank()) {
            log.warn("OneSignal app-id not configured, skipping push notification");
            return PushResponse.builder().success(false).errorMessage("OneSignal not configured").build();
        }

        try {
            Map<String, Object> payload = buildPayload(request);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            log.debug("Push notification sent to {} device(s)", request.getDeviceTokens().size());
            return PushResponse.builder()
                    .success(true)
                    .invalidTokens(extractInvalidTokens(response))
                    .build();

        } catch (Exception e) {
            log.error("Failed to send push notification via OneSignal: {}", e.getMessage(), e);
            return PushResponse.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    private Map<String, Object> buildPayload(PushRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("app_id", appId);
        payload.put("include_player_ids", request.getDeviceTokens());
        payload.put("headings", Map.of("en", request.getTitle() != null ? request.getTitle() : ""));
        payload.put("contents", Map.of("en", request.getBody() != null ? request.getBody() : ""));

        if (request.getData() != null && !request.getData().isEmpty()) {
            payload.put("data", request.getData());
        }
        if (request.getDeepLink() != null) {
            payload.put("url", request.getDeepLink());
        }

        return payload;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractInvalidTokens(Map<String, Object> response) {
        if (response == null) return Collections.emptyList();
        try {
            Object errors = response.get("errors");
            if (errors instanceof Map) {
                Object invalid = ((Map<?, ?>) errors).get("invalid_player_ids");
                if (invalid instanceof List) {
                    return (List<String>) invalid;
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }
}
