package com.socialwallet.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.store.StoreException;
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
    return repository.save(event);
  }

  public StoreWebhookEvent process(StoreWebhookEvent event, Runnable handler) {
    incrementAttempts(event);
    try {
      handler.run();
      event.setStatus(StoreWebhookEventStatus.PROCESSED);
      event.setProcessedAt(Instant.now());
      event.setLastError(null);
      return repository.save(event);
    } catch (RuntimeException ex) {
      String message = ex.getMessage();
      if (!StringUtils.hasText(message)) {
        message = ex.getClass().getSimpleName();
      }
      event.setStatus(StoreWebhookEventStatus.FAILED);
      event.setLastError(message);
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

  private void incrementAttempts(StoreWebhookEvent event) {
    event.setAttempts(event.getAttempts() + 1);
    repository.save(event);
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
