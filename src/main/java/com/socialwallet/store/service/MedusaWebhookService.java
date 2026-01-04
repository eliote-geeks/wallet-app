package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.store.MedusaProperties;
import com.socialwallet.store.StoreException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedusaWebhookService {
  private final MedusaProperties properties;
  private final ObjectMapper objectMapper;

  public void handle(String payload, Map<String, String> headers) {
    String signature = findHeader(headers, "x-medusa-signature");
    if (!isSignatureValid(payload, signature)) {
      throw new StoreException(HttpStatus.UNAUTHORIZED, "Invalid Medusa webhook signature");
    }
    String event = findHeader(headers, "x-medusa-event");
    if (!StringUtils.hasText(event)) {
      event = extractEvent(payload);
    }
    log.info("Medusa webhook received event={} payloadSize={}", event, payload == null ? 0 : payload.length());
  }

  private boolean isSignatureValid(String payload, String signature) {
    String secret = properties.getWebhookSecret();
    if (!StringUtils.hasText(secret)) {
      return true;
    }
    if (!StringUtils.hasText(signature)) {
      return false;
    }
    String normalized = signature.trim();
    if (normalized.startsWith("sha256=")) {
      normalized = normalized.substring("sha256=".length());
    }
    String computed = hmacSha256Hex(secret, payload == null ? "" : payload);
    return MessageDigest.isEqual(computed.getBytes(StandardCharsets.UTF_8),
      normalized.getBytes(StandardCharsets.UTF_8));
  }

  private String hmacSha256Hex(String secret, String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] result = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(result);
    } catch (Exception ex) {
      throw new StoreException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to verify webhook signature");
    }
  }

  private String extractEvent(String payload) {
    if (!StringUtils.hasText(payload)) {
      return "unknown";
    }
    try {
      JsonNode node = objectMapper.readTree(payload);
      if (node.hasNonNull("event")) {
        return node.get("event").asText();
      }
      if (node.hasNonNull("event_name")) {
        return node.get("event_name").asText();
      }
      if (node.hasNonNull("type")) {
        return node.get("type").asText();
      }
    } catch (Exception ex) {
      return "unknown";
    }
    return "unknown";
  }

  private String findHeader(Map<String, String> headers, String name) {
    if (headers == null || headers.isEmpty()) {
      return "";
    }
    String lower = name.toLowerCase(Locale.ROOT);
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey() != null && entry.getKey().toLowerCase(Locale.ROOT).equals(lower)) {
        return entry.getValue();
      }
    }
    return "";
  }
}
