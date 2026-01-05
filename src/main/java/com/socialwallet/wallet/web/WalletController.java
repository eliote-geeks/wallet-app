package com.socialwallet.wallet.web;

import com.socialwallet.wallet.application.WalletPaymentService;
import com.socialwallet.wallet.dto.WalletBalanceDto;
import com.socialwallet.wallet.dto.WalletTopupRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {
  private final WalletPaymentService walletPaymentService;

  @GetMapping("/balance")
  public ResponseEntity<WalletBalanceDto> getBalance(Principal principal) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.getBalance(userId));
  }

  @PostMapping("/topup")
  public ResponseEntity<WalletBalanceDto> topUp(Principal principal,
                                                @Valid @RequestBody WalletTopupRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(walletPaymentService.topUp(userId, request.getCurrency(), request.getAmount()));
  }
}
