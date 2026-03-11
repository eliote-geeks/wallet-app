package com.socialwallet.payments.mobilemoney;

import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupResponse;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWebhookRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawResponse;
import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.application.WalletPaymentService;
import com.socialwallet.wallet.model.WalletTransaction;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileMoneyService {
  private final WalletPaymentService walletPaymentService;
  private final MobileMoneyProvider provider;

  public MobileMoneyTopupResponse initiateTopup(UUID userId, MobileMoneyTopupRequest request) {
    WalletTransaction tx = walletPaymentService.createPendingTopup(userId, request.getCurrency(), request.getAmount(),
      request.getProvider(), null);
    MobileMoneyProviderResponse providerResponse = provider.initiateTopup(tx.getId(), request);
    walletPaymentService.updateTransactionReference(tx.getId(), providerResponse.getProviderReference());

    MobileMoneyTopupResponse response = new MobileMoneyTopupResponse();
    response.setTransactionId(tx.getId());
    response.setStatus(providerResponse.getStatus());
    response.setProvider(providerResponse.getProvider());
    response.setProviderReference(providerResponse.getProviderReference());
    response.setMessage(providerResponse.getMessage());
    return response;
  }

  public MobileMoneyWithdrawResponse initiateWithdraw(UUID userId, MobileMoneyWithdrawRequest request) {
    WalletTransaction tx = walletPaymentService.createPendingWithdraw(userId, request.getCurrency(), request.getAmount(),
      request.getProvider(), request.getPhoneNumber());
    MobileMoneyProviderResponse providerResponse = provider.initiateWithdraw(tx.getId(), request);
    walletPaymentService.updateTransactionReference(tx.getId(), providerResponse.getProviderReference());

    MobileMoneyWithdrawResponse response = new MobileMoneyWithdrawResponse();
    response.setTransactionId(tx.getId());
    response.setStatus(providerResponse.getStatus());
    response.setProvider(providerResponse.getProvider());
    response.setProviderReference(providerResponse.getProviderReference());
    response.setMessage(providerResponse.getMessage());
    return response;
  }

  public void handleWebhook(MobileMoneyWebhookRequest request) {
    if (request == null || request.getTransactionId() == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "transactionId is required");
    }
    String type = normalize(request.getType());
    String status = normalize(request.getStatus());
    if (!StringUtils.hasText(type) || !StringUtils.hasText(status)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "type and status are required");
    }

    boolean success = status.equals("SUCCESS") || status.equals("COMPLETED");
    if (type.equals("TOPUP")) {
      if (success) {
        walletPaymentService.completePendingTopup(request.getTransactionId());
      } else {
        walletPaymentService.failPendingTopup(request.getTransactionId(), request.getReason());
      }
      return;
    }
    if (type.equals("WITHDRAW")) {
      if (success) {
        walletPaymentService.completePendingWithdraw(request.getTransactionId());
      } else {
        walletPaymentService.failPendingWithdraw(request.getTransactionId(), request.getReason());
      }
      return;
    }
    log.warn("Unsupported mobile money webhook type={}", type);
  }

  private String normalize(String value) {
    if (!StringUtils.hasText(value)) {
      return "";
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
