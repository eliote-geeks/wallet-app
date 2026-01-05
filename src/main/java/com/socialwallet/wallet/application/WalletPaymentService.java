package com.socialwallet.wallet.application;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class WalletPaymentService {
  public void authorize(UUID userId, String cartId, String currency, Long amount) {
    log.info("Wallet payment stub: userId={}, cartId={}, currency={}, amount={}", userId, cartId, currency, amount);
  }
}
