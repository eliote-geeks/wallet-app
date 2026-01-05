package com.socialwallet.payments.mobilemoney;

import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawRequest;
import java.util.UUID;

public interface MobileMoneyProvider {
  MobileMoneyProviderResponse initiateTopup(UUID transactionId, MobileMoneyTopupRequest request);

  MobileMoneyProviderResponse initiateWithdraw(UUID transactionId, MobileMoneyWithdrawRequest request);
}
