package com.socialwallet.wallet.application;

import com.socialwallet.wallet.dto.WalletLedgerAccountDto;
import com.socialwallet.wallet.dto.WalletLedgerDiagnosticsResponse;
import com.socialwallet.wallet.model.WalletAccount;
import com.socialwallet.wallet.model.WalletEntryBalanceType;
import com.socialwallet.wallet.repository.WalletAccountRepository;
import com.socialwallet.wallet.repository.WalletEntryRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletLedgerService {
  private final WalletAccountRepository accountRepository;
  private final WalletEntryRepository entryRepository;

  @Transactional(readOnly = true)
  public WalletLedgerDiagnosticsResponse diagnose() {
    List<WalletAccount> accounts = accountRepository.findAll();
    List<WalletLedgerAccountDto> items = new ArrayList<>();
    int mismatched = 0;
    for (WalletAccount account : accounts) {
      long computedAvailable = sumFor(account.getId(), WalletEntryBalanceType.AVAILABLE);
      long computedReserved = sumFor(account.getId(), WalletEntryBalanceType.RESERVED);
      long storedAvailable = valueOrZero(account.getAvailableAmount());
      long storedReserved = valueOrZero(account.getReservedAmount());
      boolean mismatch = computedAvailable != storedAvailable || computedReserved != storedReserved;
      if (mismatch) {
        mismatched++;
      }
      items.add(toDto(account, storedAvailable, storedReserved, computedAvailable, computedReserved, mismatch));
    }
    WalletLedgerDiagnosticsResponse response = new WalletLedgerDiagnosticsResponse();
    response.setTotalAccounts(accounts.size());
    response.setMismatchedAccounts(mismatched);
    response.setAccounts(items);
    return response;
  }

  @Transactional
  public WalletLedgerDiagnosticsResponse recalculate() {
    List<WalletAccount> accounts = accountRepository.findAll();
    List<WalletLedgerAccountDto> items = new ArrayList<>();
    int mismatched = 0;
    for (WalletAccount account : accounts) {
      long computedAvailable = sumFor(account.getId(), WalletEntryBalanceType.AVAILABLE);
      long computedReserved = sumFor(account.getId(), WalletEntryBalanceType.RESERVED);
      long storedAvailable = valueOrZero(account.getAvailableAmount());
      long storedReserved = valueOrZero(account.getReservedAmount());
      boolean mismatch = computedAvailable != storedAvailable || computedReserved != storedReserved;
      if (mismatch) {
        account.setAvailableAmount(computedAvailable);
        account.setReservedAmount(computedReserved);
        accountRepository.save(account);
        mismatched++;
      }
      items.add(toDto(account, storedAvailable, storedReserved, computedAvailable, computedReserved, mismatch));
    }
    WalletLedgerDiagnosticsResponse response = new WalletLedgerDiagnosticsResponse();
    response.setTotalAccounts(accounts.size());
    response.setMismatchedAccounts(mismatched);
    response.setAccounts(items);
    return response;
  }

  private long sumFor(java.util.UUID accountId, WalletEntryBalanceType balanceType) {
    Long sum = entryRepository.sumForBalanceType(accountId, balanceType);
    return sum == null ? 0L : sum;
  }

  private long valueOrZero(Long value) {
    return value == null ? 0L : value;
  }

  private WalletLedgerAccountDto toDto(WalletAccount account,
                                       long storedAvailable,
                                       long storedReserved,
                                       long computedAvailable,
                                       long computedReserved,
                                       boolean mismatch) {
    WalletLedgerAccountDto dto = new WalletLedgerAccountDto();
    dto.setAccountId(account.getId());
    dto.setUserId(account.getUserId());
    dto.setCurrency(account.getCurrencyCode());
    dto.setStoredAvailable(storedAvailable);
    dto.setStoredReserved(storedReserved);
    dto.setComputedAvailable(computedAvailable);
    dto.setComputedReserved(computedReserved);
    dto.setMismatch(mismatch);
    return dto;
  }
}
