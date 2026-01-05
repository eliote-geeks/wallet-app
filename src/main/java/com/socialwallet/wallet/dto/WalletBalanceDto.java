package com.socialwallet.wallet.dto;

import lombok.Data;

@Data
public class WalletBalanceDto {
  private String currency;
  private Long available;
  private Long reserved;
}
