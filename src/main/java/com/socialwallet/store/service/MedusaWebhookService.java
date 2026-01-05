package com.socialwallet.store.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.store.MedusaProperties;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.application.WalletPaymentService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
  private final StoreWebhookEventService eventService;
  private final WalletPaymentService walletPaymentService;

  private static final Set<String> CAPTURE_EVENTS = Set.of(
    "payment.captured",
    "order.placed",
    "order.completed"
  );
  private static final Set<String> RELEASE_EVENTS = Set.of(
    "payment.failed",
    "order.canceled",
    "order.cancelled",
    "order.refunded"
  );

  public void handle(String payload, Map<String, String> headers) {
    String signature = findHeader(headers, "x-medusa-signature");
    String event = findHeader(headers, "x-medusa-event");
    if (!StringUtils.hasText(event)) {
      event = extractEvent(payload);
    }
    StoreWebhookEvent record = eventService.record("medusa", event, payload, headers, signature);
    process(record, payload, signature, event);
  }

  public StoreWebhookEvent retry(StoreWebhookEvent record) {
    return process(record, record.getPayload(), record.getSignature(), record.getEventName());
  }

  private StoreWebhookEvent process(StoreWebhookEvent record,
                                    String payload,
                                    String signature,
                                    String event) {
    return eventService.process(record, () -> {
      if (!isSignatureValid(payload, signature)) {
        throw new StoreException(HttpStatus.UNAUTHORIZED, "Invalid Medusa webhook signature");
      }
      log.info("Medusa webhook received event={} payloadSize={}", event, payload == null ? 0 : payload.length());
      handleWalletEvent(event, payload);
    });
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

  private void handleWalletEvent(String event, String payload) {
    if (!StringUtils.hasText(event) || !StringUtils.hasText(payload)) {
      return;
    }
    String normalizedEvent = event.trim().toLowerCase(Locale.ROOT);
    boolean capture = CAPTURE_EVENTS.contains(normalizedEvent);
    boolean release = RELEASE_EVENTS.contains(normalizedEvent);
    if (!capture && !release) {
      return;
    }
    WalletEventData data = extractWalletData(payload);
    if (data == null || data.cartId == null || data.userId == null) {
      log.debug("Medusa webhook missing wallet data for event={}", event);
      return;
    }
    if (walletPaymentService.findHold(data.userId, data.cartId).isEmpty()) {
      log.debug("No wallet hold for cartId={} event={}", data.cartId, event);
      return;
    }
    try {
      if (capture) {
        walletPaymentService.capture(data.userId, data.cartId);
      } else {
        walletPaymentService.release(data.userId, data.cartId, "medusa:" + normalizedEvent);
      }
    } catch (WalletException ex) {
      if (ex.getStatus() == HttpStatus.NOT_FOUND) {
        log.debug("Wallet hold not found for cartId={} event={}", data.cartId, event);
        return;
      }
      throw ex;
    }
  }

  private WalletEventData extractWalletData(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      String cartId = firstNonBlank(
        textAt(root, "data", "cart_id"),
        textAt(root, "data", "order", "cart_id"),
        textAt(root, "data", "cart", "id"),
        textAt(root, "data", "payment", "cart_id"),
        textAt(root, "data", "payment_collection", "cart_id")
      );
      String userId = firstNonBlank(
        textAt(root, "data", "metadata", "kobo_user_id"),
        textAt(root, "data", "order", "metadata", "kobo_user_id"),
        textAt(root, "data", "cart", "metadata", "kobo_user_id"),
        textAt(root, "data", "payment", "metadata", "kobo_user_id")
      );
      if (!StringUtils.hasText(cartId) || !StringUtils.hasText(userId)) {
        return null;
      }
      return new WalletEventData(cartId, UUID.fromString(userId));
    } catch (Exception ex) {
      log.debug("Unable to parse wallet data from Medusa payload", ex);
      return null;
    }
  }

  private String textAt(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      if (current == null || current.isMissingNode() || current.isNull()) {
        return null;
      }
      current = current.path(segment);
    }
    if (current == null || current.isMissingNode() || current.isNull()) {
      return null;
    }
    String value = current.asText();
    return StringUtils.hasText(value) ? value : null;
  }

  private String firstNonBlank(String... values) {
    if (values == null) {
      return null;
    }
    for (String value : values) {
      if (StringUtils.hasText(value)) {
        return value;
      }
    }
    return null;
  }

  private static class WalletEventData {
    private final String cartId;
    private final UUID userId;

    private WalletEventData(String cartId, UUID userId) {
      this.cartId = cartId;
      this.userId = userId;
    }
  }
}
