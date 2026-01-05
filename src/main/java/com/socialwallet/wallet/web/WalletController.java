package com.socialwallet.wallet.web;

import com.socialwallet.wallet.WalletException;
import com.socialwallet.wallet.application.WalletPaymentService;
import com.socialwallet.wallet.dto.WalletBalanceDto;
import com.socialwallet.wallet.dto.WalletTopupRequest;
import com.socialwallet.wallet.dto.WalletTransferRequest;
import com.socialwallet.wallet.dto.WalletTransferResponse;
import com.socialwallet.wallet.dto.WalletTransactionDto;
import com.socialwallet.wallet.dto.WalletWithdrawRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
  private final WalletPaymentService walletPaymentService;

  @GetMapping("/balance")
  public ResponseEntity<List<WalletBalanceDto>> listBalances(Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.listBalances(userId));
  }

  @GetMapping("/balance/{currency}")
  public ResponseEntity<WalletBalanceDto> getBalance(Principal principal, @PathVariable String currency) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.getBalance(userId, currency));
  }

  @PostMapping("/topup")
  public ResponseEntity<WalletBalanceDto> topUp(Principal principal,
                                                @Valid @RequestBody WalletTopupRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.topUp(userId, request.getCurrency(), request.getAmount()));
  }

  @PostMapping("/withdraw")
  public ResponseEntity<WalletBalanceDto> withdraw(Principal principal,
                                                   @Valid @RequestBody WalletWithdrawRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.withdraw(userId, request.getCurrency(), request.getAmount(),
      request.getDestination()));
  }

  @PostMapping("/transfer")
  public ResponseEntity<WalletTransferResponse> transfer(Principal principal,
                                                         @Valid @RequestBody WalletTransferRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    UUID recipientId;
    try {
      recipientId = UUID.fromString(request.getRecipientId());
    } catch (IllegalArgumentException ex) {
      throw new WalletException(org.springframework.http.HttpStatus.BAD_REQUEST, "recipientId must be a UUID");
    }
    return ResponseEntity.ok(walletPaymentService.transfer(userId, recipientId, request.getCurrency(),
      request.getAmount(), request.getNote()));
  }

  @GetMapping("/transactions")
  public ResponseEntity<List<WalletTransactionDto>> listTransactions(Principal principal,
                                                                     @RequestParam(defaultValue = "50") int limit) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.listTransactions(userId, limit));
  }
}
