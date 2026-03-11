package com.socialwallet.store.dto;

import com.socialwallet.store.model.StoreSellerStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class StoreSellerProfileResponse {
  private UUID id;
  private UUID userId;
  private String shopName;
  private String description;
  private StoreSellerStatus status;
  private Instant createdAt;
}

