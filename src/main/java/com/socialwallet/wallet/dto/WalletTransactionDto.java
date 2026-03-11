package com.socialwallet.wallet.dto;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Data
public class WalletTransactionDto {
  private UUID id;
  private String type;
  private String status;
  private String currency;
  private Long amount;
  private String referenceType;
  private String referenceId;
  private Instant createdAt;
}
