package com.socialwallet.wallet.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class WalletTransferResponse {
  private UUID transactionId;
  private WalletBalanceDto balance;
}
