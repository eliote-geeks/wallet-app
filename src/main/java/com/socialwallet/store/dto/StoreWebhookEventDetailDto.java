package com.socialwallet.store.dto;

import lombok.Data;

@Data
public class StoreWebhookEventDetailDto extends StoreWebhookEventSummaryDto {
  private String payload;
  private String headers;
  private String signature;
}
