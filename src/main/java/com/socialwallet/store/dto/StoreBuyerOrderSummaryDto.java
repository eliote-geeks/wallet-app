package com.socialwallet.store.dto;

import java.time.Instant;
import lombok.Data;

@Data
public class StoreBuyerOrderSummaryDto {
  private String orderId;
  private String cartId;
  private String currencyCode;
  private Long totalAmount;
  private String orderStatus;
  private String paymentStatus;
  private String fulfillmentStatus;
  private Instant createdAt;
  private Instant updatedAt;
}

