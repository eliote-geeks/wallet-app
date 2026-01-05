package com.socialwallet.store.dto;

import com.socialwallet.store.model.StoreWebhookEventStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class StoreWebhookEventSummaryDto {
  private UUID id;
  private String provider;
  private String eventName;
  private StoreWebhookEventStatus status;
  private int attempts;
  private String lastError;
  private Instant createdAt;
  private Instant processedAt;
}
