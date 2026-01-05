package com.socialwallet.payments.mobilemoney.dto;

import java.util.UUID;
import lombok.Data;

@Data
public class MobileMoneyTopupResponse {
  private UUID transactionId;
  private String status;
  private String provider;
  private String providerReference;
  private String message;
}
