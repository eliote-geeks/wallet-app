package com.socialwallet.notifications.provider.impl;

import com.socialwallet.notifications.provider.SmsProvider;
import com.socialwallet.notifications.provider.SmsRequest;
import com.socialwallet.notifications.provider.SmsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Africa's Talking SMS API implementation.
 * Best coverage for Cameroon and Central/West Africa.
 * Cost: ~0.02-0.08 USD per SMS.
 */
@Component
public class AfricasTalkingSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(AfricasTalkingSmsProvider.class);
    private static final String AT_API_URL = "https://api.africastalking.com/version1/messaging";
    private static final String AT_SANDBOX_URL = "https://api.sandbox.africastalking.com/version1/messaging";

    private final RestClient restClient;
    private final String username;
    private final boolean sandbox;

    public AfricasTalkingSmsProvider(
            @Value("${notifications.africastalking.api-key:}") String apiKey,
            @Value("${notifications.africastalking.username:}") String username,
            @Value("${notifications.africastalking.sandbox:true}") boolean sandbox) {
        this.username = username;
        this.sandbox = sandbox;

        String baseUrl = sandbox ? AT_SANDBOX_URL : AT_API_URL;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("apiKey", apiKey)
                .build();
    }

    @Override
    public SmsResponse send(SmsRequest request) {
        if (request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
            return SmsResponse.builder().success(false).errorMessage("No phone number provided").build();
        }

        if (username == null || username.isBlank()) {
            log.warn("Africa's Talking username not configured, skipping SMS");
            return SmsResponse.builder().success(false).errorMessage("Africa's Talking not configured").build();
        }

        try {
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("username", sandbox ? "sandbox" : username);
            formData.add("to", request.getPhoneNumber());
            formData.add("message", request.getMessage());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(Map.class);

            log.debug("SMS sent to {}: response={}", maskPhone(request.getPhoneNumber()), response);
            return SmsResponse.builder()
                    .success(true)
                    .messageId(extractMessageId(response))
                    .build();

        } catch (Exception e) {
            log.error("Failed to send SMS via Africa's Talking to {}: {}",
                    maskPhone(request.getPhoneNumber()), e.getMessage(), e);
            return SmsResponse.builder().success(false).errorMessage(e.getMessage()).build();
        }
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, phone.length() - 4) + "****";
    }

    @SuppressWarnings("unchecked")
    private String extractMessageId(Map<String, Object> response) {
        try {
            if (response == null) return null;
            Map<String, Object> smsData = (Map<String, Object>) response.get("SMSMessageData");
            if (smsData == null) return null;
            var recipients = (List<Map<String, Object>>) smsData.get("Recipients");
            if (recipients != null && !recipients.isEmpty()) {
                return (String) recipients.get(0).get("messageId");
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
