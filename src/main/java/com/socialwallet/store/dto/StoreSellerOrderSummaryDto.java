package com.socialwallet.store.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class StoreSellerOrderSummaryDto {
  private String orderId;
  private UUID sellerUserId;
  private UUID buyerUserId;
  private String cartId;
  private String currency;
  private Long grossAmount;
  private Long platformFeeAmount;
  private Long netAmount;
  private String status;
  private String orderStatus;
  private String paymentStatus;
  private String fulfillmentStatus;
  private UUID settledWalletTxId;
  private Instant settledAt;
  private Instant createdAt;
}
