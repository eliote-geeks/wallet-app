package com.socialwallet.payments.mobilemoney.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Data;

@Data
public class MobileMoneyWebhookRequest {
  @NotNull
  private UUID transactionId;

  @NotBlank
  private String status;

  @NotBlank
  private String type;

  private String reason;
}
