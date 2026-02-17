package com.socialwallet.store.service;

import com.socialwallet.store.StoreWebhookRetryProperties;
import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.store.model.StoreWebhookEventStatus;
import com.socialwallet.store.repository.StoreWebhookEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedusaWebhookRetryJob {
  private final StoreWebhookEventRepository repository;
  private final MedusaWebhookService webhookService;
  private final StoreWebhookRetryProperties retryProperties;

  @Scheduled(fixedDelayString = "${app.store.webhooks.medusa.retry.fixed-delay-ms:5000}")
  public void retryFailedEvents() {
    if (!retryProperties.isEnabled()) {
      return;
    }

    Instant now = Instant.now();
    int batchSize = Math.min(Math.max(retryProperties.getBatchSize(), 1), 200);
    int maxAttempts = Math.max(retryProperties.getMaxAttempts(), 1);

    List<UUID> dueIds = repository.findDueIdsForRetry(
      "medusa",
      StoreWebhookEventStatus.FAILED,
      now,
      maxAttempts,
      PageRequest.of(0, batchSize)
    );

    if (dueIds.isEmpty()) {
      return;
    }

    for (UUID id : dueIds) {
      try {
        int claimed = repository.transitionStatus(id, StoreWebhookEventStatus.FAILED, StoreWebhookEventStatus.PROCESSING, Instant.now());
        if (claimed == 0) {
          continue;
        }

        StoreWebhookEvent event = repository.findById(id).orElse(null);
        if (event == null) {
          continue;
        }
        webhookService.retry(event);
      } catch (Exception ex) {
        // The underlying process() already records FAILED + lastError + nextRetryAt.
        log.warn("Medusa webhook auto-retry failed for eventId={}: {}", id, ex.getMessage());
      }
    }
  }
}

