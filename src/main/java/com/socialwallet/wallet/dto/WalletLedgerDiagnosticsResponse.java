package com.socialwallet.wallet.dto;

import java.util.List;
import lombok.Data;

@Data
public class WalletLedgerDiagnosticsResponse {
  private int totalAccounts;
  private int mismatchedAccounts;
  private List<WalletLedgerAccountDto> accounts;
}
