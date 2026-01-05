package com.socialwallet.wallet.application;

import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.dto.WalletBalanceDto;
import com.socialwallet.wallet.model.WalletAccount;
import com.socialwallet.wallet.model.WalletHold;
import com.socialwallet.wallet.model.WalletHoldStatus;
import com.socialwallet.wallet.repository.WalletAccountRepository;
import com.socialwallet.wallet.repository.WalletHoldRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletPaymentService {
  private final WalletAccountRepository accountRepository;
  private final WalletHoldRepository holdRepository;

  @Transactional
  public WalletHold authorize(UUID userId, String cartId, String currency, Long amount) {
    validateInput(userId, cartId, currency, amount);
    String normalizedCurrency = normalizeCurrency(currency);
    WalletAccount account = getOrCreateAccount(userId, normalizedCurrency);
    WalletHold existing = holdRepository.findByUserIdAndCartId(userId, cartId).orElse(null);

    if (existing != null) {
      if (existing.getStatus() == WalletHoldStatus.AUTHORIZED || existing.getStatus() == WalletHoldStatus.CAPTURED) {
        return existing;
      }
      if (!normalizedCurrency.equalsIgnoreCase(existing.getCurrencyCode()) || !amount.equals(existing.getAmount())) {
        throw new WalletException(HttpStatus.CONFLICT, "Wallet hold exists for this cart with different amount or currency");
      }
    }

    if (account.getAvailableAmount() < amount) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "Insufficient wallet balance");
    }

    account.setAvailableAmount(account.getAvailableAmount() - amount);
    account.setReservedAmount(account.getReservedAmount() + amount);
    accountRepository.save(account);

    WalletHold hold = existing != null ? existing : new WalletHold();
    hold.setUserId(userId);
    hold.setCartId(cartId);
    hold.setCurrencyCode(normalizedCurrency);
    hold.setAmount(amount);
    hold.setStatus(WalletHoldStatus.AUTHORIZED);
    hold.setFailureReason(null);
    WalletHold saved = holdRepository.save(hold);
    log.info("Wallet authorized: userId={}, cartId={}, amount={}, currency={}", userId, cartId, amount, normalizedCurrency);
    return saved;
  }

  @Transactional
  public WalletHold capture(UUID userId, String cartId) {
    if (userId == null || !StringUtils.hasText(cartId)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId and cartId are required");
    }
    WalletHold hold = holdRepository.findByUserIdAndCartId(userId, cartId)
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet hold not found"));

    if (hold.getStatus() == WalletHoldStatus.CAPTURED) {
      return hold;
    }
    if (hold.getStatus() != WalletHoldStatus.AUTHORIZED) {
      throw new WalletException(HttpStatus.CONFLICT, "Wallet hold is not in an authorized state");
    }

    WalletAccount account = accountRepository.findForUpdate(userId)
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet account not found"));
    Long amount = hold.getAmount();
    account.setReservedAmount(account.getReservedAmount() - amount);
    accountRepository.save(account);

    hold.setStatus(WalletHoldStatus.CAPTURED);
    WalletHold saved = holdRepository.save(hold);
    log.info("Wallet captured: userId={}, cartId={}, amount={}", userId, cartId, amount);
    return saved;
  }

  @Transactional
  public WalletHold release(UUID userId, String cartId, String reason) {
    if (userId == null || !StringUtils.hasText(cartId)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId and cartId are required");
    }
    WalletHold hold = holdRepository.findByUserIdAndCartId(userId, cartId)
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet hold not found"));

    if (hold.getStatus() == WalletHoldStatus.RELEASED) {
      return hold;
    }
    if (hold.getStatus() == WalletHoldStatus.CAPTURED) {
      throw new WalletException(HttpStatus.CONFLICT, "Wallet hold already captured");
    }

    WalletAccount account = accountRepository.findForUpdate(userId)
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet account not found"));
    Long amount = hold.getAmount();
    account.setReservedAmount(account.getReservedAmount() - amount);
    account.setAvailableAmount(account.getAvailableAmount() + amount);
    accountRepository.save(account);

    hold.setStatus(WalletHoldStatus.RELEASED);
    hold.setFailureReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
    WalletHold saved = holdRepository.save(hold);
    log.info("Wallet released: userId={}, cartId={}, amount={}, reason={}", userId, cartId, amount, reason);
    return saved;
  }

  @Transactional
  public WalletBalanceDto topUp(UUID userId, String currency, Long amount) {
    validateInput(userId, "topup", currency, amount);
    String normalizedCurrency = normalizeCurrency(currency);
    WalletAccount account = getOrCreateAccount(userId, normalizedCurrency);
    account.setAvailableAmount(account.getAvailableAmount() + amount);
    accountRepository.save(account);

    WalletBalanceDto dto = new WalletBalanceDto();
    dto.setCurrency(account.getCurrencyCode());
    dto.setAvailable(account.getAvailableAmount());
    dto.setReserved(account.getReservedAmount());
    log.info("Wallet topup: userId={}, amount={}, currency={}", userId, amount, normalizedCurrency);
    return dto;
  }

  @Transactional(readOnly = true)
  public WalletBalanceDto getBalance(UUID userId) {
    if (userId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    WalletAccount account = accountRepository.findById(userId)
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet account not found"));
    WalletBalanceDto dto = new WalletBalanceDto();
    dto.setCurrency(account.getCurrencyCode());
    dto.setAvailable(account.getAvailableAmount());
    dto.setReserved(account.getReservedAmount());
    return dto;
  }

  private WalletAccount getOrCreateAccount(UUID userId, String currency) {
    WalletAccount account = accountRepository.findForUpdate(userId).orElse(null);
    if (account == null) {
      WalletAccount created = new WalletAccount();
      created.setUserId(userId);
      created.setCurrencyCode(currency);
      created.setAvailableAmount(0L);
      created.setReservedAmount(0L);
      return accountRepository.save(created);
    }
    if (!account.getCurrencyCode().equalsIgnoreCase(currency)) {
      throw new WalletException(HttpStatus.CONFLICT, "Wallet currency mismatch");
    }
    return account;
  }

  private void validateInput(UUID userId, String cartId, String currency, Long amount) {
    if (userId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    if (!StringUtils.hasText(cartId)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "cartId is required");
    }
    if (!StringUtils.hasText(currency)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "currency is required");
    }
    if (amount == null || amount <= 0) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "amount must be greater than 0");
    }
  }

  private String normalizeCurrency(String currency) {
    return currency.trim().toUpperCase(Locale.ROOT);
  }
}
