package com.socialwallet.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.store.StoreException;
import com.socialwallet.store.StoreWebhookRetryProperties;
import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.store.model.StoreWebhookEventStatus;
import com.socialwallet.store.repository.StoreWebhookEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class StoreWebhookEventService {
  private final StoreWebhookEventRepository repository;
  private final ObjectMapper objectMapper;
  private final StoreWebhookRetryProperties retryProperties;

  public StoreWebhookEvent record(String provider,
                                  String eventName,
                                  String payload,
                                  Map<String, String> headers,
                                  String signature) {
    StoreWebhookEvent event = new StoreWebhookEvent();
    event.setProvider(provider);
    event.setEventName(eventName);
    event.setPayload(payload);
    event.setSignature(signature);
    event.setHeaders(serializeHeaders(headers));
    event.setStatus(StoreWebhookEventStatus.RECEIVED);
    event.setAttempts(0);
    event.setNextRetryAt(null);
    return repository.save(event);
  }

  public StoreWebhookEvent process(StoreWebhookEvent event, Runnable handler) {
    markProcessing(event);
    try {
      handler.run();
      event.setStatus(StoreWebhookEventStatus.PROCESSED);
      event.setProcessedAt(Instant.now());
      event.setLastError(null);
      event.setNextRetryAt(null);
      return repository.save(event);
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (!StringUtils.hasText(message)) {
        message = ex.getClass().getSimpleName();
      }
      event.setStatus(StoreWebhookEventStatus.FAILED);
      event.setLastError(message);
      event.setProcessedAt(null);
      event.setNextRetryAt(computeNextRetryAt(event.getAttempts()));
      repository.save(event);
      throw ex;
    }
  }

  public StoreWebhookEvent get(UUID id) {
    return repository.findById(id)
      .orElseThrow(() -> new StoreException(HttpStatus.NOT_FOUND, "Webhook event not found"));
  }

  public Page<StoreWebhookEvent> list(StoreWebhookEventStatus status, Pageable pageable) {
    if (status == null) {
      return repository.findAll(pageable);
    }
    return repository.findByStatus(status, pageable);
  }

  private void markProcessing(StoreWebhookEvent event) {
    event.setAttempts(event.getAttempts() + 1);
    event.setStatus(StoreWebhookEventStatus.PROCESSING);
    event.setProcessedAt(null);
    event.setLastError(null);
    event.setNextRetryAt(null);
    repository.save(event);
  }

  private Instant computeNextRetryAt(int attempts) {
    int maxAttempts = Math.max(retryProperties.getMaxAttempts(), 1);
    if (attempts >= maxAttempts) {
      return null;
    }

    long base = Math.max(retryProperties.getBaseDelaySeconds(), 1L);
    long max = Math.max(retryProperties.getMaxDelaySeconds(), base);

    long delay = base;
    for (int i = 1; i < attempts; i++) {
      if (delay >= max) {
        delay = max;
        break;
      }
      delay = Math.min(max, delay * 2);
    }

    return Instant.now().plusSeconds(delay);
  }

  private String serializeHeaders(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      return "";
    }
    try {
      return objectMapper.writeValueAsString(headers);
    } catch (JsonProcessingException ex) {
      return "";
    }
  }
}
