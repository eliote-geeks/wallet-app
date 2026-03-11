package com.socialwallet.payments.mobilemoney;

import lombok.Data;

@Data
public class MobileMoneyProviderResponse {
  private String provider;
  private String providerReference;
  private String status;
  private String message;
}
