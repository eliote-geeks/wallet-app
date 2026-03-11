package com.socialwallet.payments.mobilemoney;

import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StubMobileMoneyProvider implements MobileMoneyProvider {
  @Override
  public MobileMoneyProviderResponse initiateTopup(UUID transactionId, MobileMoneyTopupRequest request) {
    MobileMoneyProviderResponse response = new MobileMoneyProviderResponse();
    response.setProvider(request.getProvider());
    response.setProviderReference("stub-topup-" + transactionId);
    response.setStatus("PENDING");
    response.setMessage("Stub provider: confirmation pending");
    return response;
  }

  @Override
  public MobileMoneyProviderResponse initiateWithdraw(UUID transactionId, MobileMoneyWithdrawRequest request) {
    MobileMoneyProviderResponse response = new MobileMoneyProviderResponse();
    response.setProvider(request.getProvider());
    response.setProviderReference("stub-withdraw-" + transactionId);
    response.setStatus("PENDING");
    response.setMessage("Stub provider: confirmation pending");
    return response;
  }
}
