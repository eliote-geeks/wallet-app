package com.socialwallet.wallet.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class WalletLedgerAccountDto {
  private UUID accountId;
  private UUID userId;
  private String currency;
  private Long storedAvailable;
  private Long storedReserved;
  private Long computedAvailable;
  private Long computedReserved;
  private boolean mismatch;
}
