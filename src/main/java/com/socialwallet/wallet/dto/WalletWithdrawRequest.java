package com.socialwallet.wallet.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WalletWithdrawRequest {
  @NotNull
  @Min(1)
  private Long amount;

  @NotBlank
  private String currency;

  private String destination;
}
