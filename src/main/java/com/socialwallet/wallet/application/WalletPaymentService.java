package com.socialwallet.wallet.application;

import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.dto.WalletBalanceDto;
import com.socialwallet.wallet.dto.WalletTransferResponse;
import com.socialwallet.wallet.dto.WalletTransactionDto;
import com.socialwallet.wallet.model.WalletAccount;
import com.socialwallet.wallet.model.WalletEntry;
import com.socialwallet.wallet.model.WalletEntryBalanceType;
import com.socialwallet.wallet.model.WalletEntryDirection;
import com.socialwallet.wallet.model.WalletHold;
import com.socialwallet.wallet.model.WalletHoldStatus;
import com.socialwallet.wallet.model.WalletTransaction;
import com.socialwallet.wallet.model.WalletTransactionStatus;
import com.socialwallet.wallet.model.WalletTransactionType;
import com.socialwallet.wallet.repository.WalletAccountRepository;
import com.socialwallet.wallet.repository.WalletEntryRepository;
import com.socialwallet.wallet.repository.WalletHoldRepository;
import com.socialwallet.wallet.repository.WalletTransactionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
  private final WalletTransactionRepository transactionRepository;
  private final WalletEntryRepository entryRepository;
  private final UserAccountRepository userAccountRepository;

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
    hold.setAccountId(account.getId());
    hold.setCartId(cartId);
    hold.setCurrencyCode(normalizedCurrency);
    hold.setAmount(amount);
    hold.setStatus(WalletHoldStatus.AUTHORIZED);
    hold.setFailureReason(null);
    WalletHold saved = holdRepository.save(hold);

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.HOLD, WalletTransactionStatus.AUTHORIZED,
      normalizedCurrency, amount, "CART", cartId, null);
    recordEntry(tx, account, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.DEBIT, amount, "Hold funds");
    recordEntry(tx, account, WalletEntryBalanceType.RESERVED, WalletEntryDirection.CREDIT, amount, "Hold funds");
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

    WalletAccount account = accountRepository.findForUpdateById(hold.getAccountId())
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet account not found"));
    Long amount = hold.getAmount();
    account.setReservedAmount(account.getReservedAmount() - amount);
    accountRepository.save(account);

    hold.setStatus(WalletHoldStatus.CAPTURED);
    WalletHold saved = holdRepository.save(hold);

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.CAPTURE, WalletTransactionStatus.COMPLETED,
      hold.getCurrencyCode(), amount, "CART", cartId, null);
    recordEntry(tx, account, WalletEntryBalanceType.RESERVED, WalletEntryDirection.DEBIT, amount, "Capture funds");

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

    WalletAccount account = accountRepository.findForUpdateById(hold.getAccountId())
      .orElseThrow(() -> new WalletException(HttpStatus.NOT_FOUND, "Wallet account not found"));
    Long amount = hold.getAmount();
    account.setReservedAmount(account.getReservedAmount() - amount);
    account.setAvailableAmount(account.getAvailableAmount() + amount);
    accountRepository.save(account);

    hold.setStatus(WalletHoldStatus.RELEASED);
    hold.setFailureReason(reason != null && reason.length() > 500 ? reason.substring(0, 500) : reason);
    WalletHold saved = holdRepository.save(hold);

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.RELEASE, WalletTransactionStatus.RELEASED,
      hold.getCurrencyCode(), amount, "CART", cartId, reason);
    recordEntry(tx, account, WalletEntryBalanceType.RESERVED, WalletEntryDirection.DEBIT, amount, "Release funds");
    recordEntry(tx, account, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.CREDIT, amount, "Release funds");

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

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.TOPUP, WalletTransactionStatus.COMPLETED,
      normalizedCurrency, amount, "TOPUP", null, null);
    recordEntry(tx, account, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.CREDIT, amount, "Topup");

    log.info("Wallet topup: userId={}, amount={}, currency={}", userId, amount, normalizedCurrency);
    return toBalanceDto(account);
  }

  @Transactional
  public WalletBalanceDto withdraw(UUID userId, String currency, Long amount, String destination) {
    validateInput(userId, "withdraw", currency, amount);
    String normalizedCurrency = normalizeCurrency(currency);
    WalletAccount account = getOrCreateAccount(userId, normalizedCurrency);
    if (account.getAvailableAmount() < amount) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "Insufficient wallet balance");
    }
    account.setAvailableAmount(account.getAvailableAmount() - amount);
    accountRepository.save(account);

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.WITHDRAW, WalletTransactionStatus.COMPLETED,
      normalizedCurrency, amount, "WITHDRAW", null, destination);
    recordEntry(tx, account, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.DEBIT, amount, "Withdraw");

    log.info("Wallet withdraw: userId={}, amount={}, currency={}", userId, amount, normalizedCurrency);
    return toBalanceDto(account);
  }

  @Transactional
  public WalletTransferResponse transfer(UUID userId, UUID recipientId, String currency, Long amount, String note) {
    validateInput(userId, "transfer", currency, amount);
    if (recipientId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "recipientId is required");
    }
    if (recipientId.equals(userId)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "Cannot transfer to self");
    }
    if (!userAccountRepository.existsById(recipientId)) {
      throw new WalletException(HttpStatus.NOT_FOUND, "Recipient not found");
    }

    String normalizedCurrency = normalizeCurrency(currency);
    WalletAccount senderAccount = getOrCreateAccount(userId, normalizedCurrency);
    WalletAccount recipientAccount = getOrCreateAccount(recipientId, normalizedCurrency);

    if (senderAccount.getAvailableAmount() < amount) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "Insufficient wallet balance");
    }

    senderAccount.setAvailableAmount(senderAccount.getAvailableAmount() - amount);
    recipientAccount.setAvailableAmount(recipientAccount.getAvailableAmount() + amount);
    accountRepository.save(senderAccount);
    accountRepository.save(recipientAccount);

    WalletTransaction tx = recordTransaction(userId, WalletTransactionType.TRANSFER, WalletTransactionStatus.COMPLETED,
      normalizedCurrency, amount, "TRANSFER", recipientId.toString(), note);
    recordEntry(tx, senderAccount, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.DEBIT, amount, "Transfer out");
    recordEntry(tx, recipientAccount, WalletEntryBalanceType.AVAILABLE, WalletEntryDirection.CREDIT, amount, "Transfer in");

    WalletTransferResponse response = new WalletTransferResponse();
    response.setTransactionId(tx.getId());
    response.setBalance(toBalanceDto(senderAccount));
    log.info("Wallet transfer: sender={}, recipient={}, amount={}, currency={}", userId, recipientId, amount, normalizedCurrency);
    return response;
  }

  @Transactional(readOnly = true)
  public WalletBalanceDto getBalance(UUID userId, String currency) {
    if (userId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    if (!StringUtils.hasText(currency)) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "currency is required");
    }
    String normalizedCurrency = normalizeCurrency(currency);
    Optional<WalletAccount> account = accountRepository.findByUserIdAndCurrencyCodeIgnoreCase(userId, normalizedCurrency);
    return account.map(this::toBalanceDto).orElseGet(() -> emptyBalance(normalizedCurrency));
  }

  @Transactional(readOnly = true)
  public List<WalletBalanceDto> listBalances(UUID userId) {
    if (userId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    List<WalletAccount> accounts = accountRepository.findByUserId(userId);
    List<WalletBalanceDto> results = new ArrayList<>();
    for (WalletAccount account : accounts) {
      results.add(toBalanceDto(account));
    }
    return results;
  }

  @Transactional(readOnly = true)
  public List<WalletTransactionDto> listTransactions(UUID userId, int limit) {
    if (userId == null) {
      throw new WalletException(HttpStatus.BAD_REQUEST, "userId is required");
    }
    List<WalletTransaction> transactions = transactionRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    List<WalletTransactionDto> results = new ArrayList<>();
    int max = limit > 0 ? Math.min(limit, transactions.size()) : transactions.size();
    for (int i = 0; i < max; i++) {
      WalletTransaction transaction = transactions.get(i);
      WalletTransactionDto dto = new WalletTransactionDto();
      dto.setId(transaction.getId());
      dto.setType(transaction.getType().name());
      dto.setStatus(transaction.getStatus().name());
      dto.setCurrency(transaction.getCurrencyCode());
      dto.setAmount(transaction.getAmount());
      dto.setReferenceType(transaction.getReferenceType());
      dto.setReferenceId(transaction.getReferenceId());
      dto.setCreatedAt(transaction.getCreatedAt());
      results.add(dto);
    }
    return results;
  }

  @Transactional(readOnly = true)
  public Optional<WalletHold> findHold(UUID userId, String cartId) {
    if (userId == null || !StringUtils.hasText(cartId)) {
      return Optional.empty();
    }
    return holdRepository.findByUserIdAndCartId(userId, cartId);
  }

  private WalletAccount getOrCreateAccount(UUID userId, String currency) {
    WalletAccount account = accountRepository.findForUpdate(userId, currency).orElse(null);
    if (account == null) {
      WalletAccount created = new WalletAccount();
      created.setUserId(userId);
      created.setCurrencyCode(currency);
      created.setAvailableAmount(0L);
      created.setReservedAmount(0L);
      return accountRepository.save(created);
    }
    return account;
  }

  private WalletBalanceDto toBalanceDto(WalletAccount account) {
    WalletBalanceDto dto = new WalletBalanceDto();
    dto.setCurrency(account.getCurrencyCode());
    dto.setAvailable(account.getAvailableAmount());
    dto.setReserved(account.getReservedAmount());
    return dto;
  }

  private WalletBalanceDto emptyBalance(String currency) {
    WalletBalanceDto dto = new WalletBalanceDto();
    dto.setCurrency(currency);
    dto.setAvailable(0L);
    dto.setReserved(0L);
    return dto;
  }

  private WalletTransaction recordTransaction(UUID userId,
                                              WalletTransactionType type,
                                              WalletTransactionStatus status,
                                              String currency,
                                              Long amount,
                                              String referenceType,
                                              String referenceId,
                                              String metadata) {
    WalletTransaction tx = new WalletTransaction();
    tx.setUserId(userId);
    tx.setType(type);
    tx.setStatus(status);
    tx.setCurrencyCode(currency);
    tx.setAmount(amount);
    tx.setReferenceType(referenceType);
    tx.setReferenceId(referenceId);
    tx.setMetadata(metadata);
    return transactionRepository.save(tx);
  }

  private void recordEntry(WalletTransaction tx,
                           WalletAccount account,
                           WalletEntryBalanceType balanceType,
                           WalletEntryDirection direction,
                           Long amount,
                           String description) {
    WalletEntry entry = new WalletEntry();
    entry.setTransactionId(tx.getId());
    entry.setAccountId(account.getId());
    entry.setBalanceType(balanceType);
    entry.setDirection(direction);
    entry.setAmount(amount);
    entry.setCurrencyCode(account.getCurrencyCode());
    entry.setDescription(description);
    entryRepository.save(entry);
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
