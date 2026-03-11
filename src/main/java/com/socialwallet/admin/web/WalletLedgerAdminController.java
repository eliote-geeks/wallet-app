package com.socialwallet.admin.web;

import com.socialwallet.wallet.application.WalletLedgerService;
import com.socialwallet.wallet.dto.WalletLedgerDiagnosticsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/wallet/ledger")
@RequiredArgsConstructor
public class WalletLedgerAdminController {
  private final WalletLedgerService ledgerService;

  @GetMapping("/diagnostics")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<WalletLedgerDiagnosticsResponse> diagnostics() {
    return ResponseEntity.ok(ledgerService.diagnose());
  }

  @PostMapping("/recalculate")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<WalletLedgerDiagnosticsResponse> recalculate() {
    return ResponseEntity.ok(ledgerService.recalculate());
  }
}
