package com.socialwallet.store.dto;

import com.socialwallet.store.model.StoreSellerApplicationStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class StoreSellerApplicationResponse {
  private UUID id;
  private UUID userId;
  private String shopName;
  private String description;
  private StoreSellerApplicationStatus status;
  private String rejectionReason;
  private Instant decidedAt;
  private UUID decidedByUserId;
  private Instant createdAt;
}

