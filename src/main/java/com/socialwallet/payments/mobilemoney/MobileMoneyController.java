package com.socialwallet.payments.mobilemoney;

import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyTopupResponse;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawRequest;
import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWithdrawResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments/mobile-money")
@RequiredArgsConstructor
public class MobileMoneyController {
  private final MobileMoneyService mobileMoneyService;

  @PostMapping("/topups")
  public ResponseEntity<MobileMoneyTopupResponse> initiateTopup(Principal principal,
                                                                @Valid @RequestBody MobileMoneyTopupRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(mobileMoneyService.initiateTopup(userId, request));
  }

  @PostMapping("/withdrawals")
  public ResponseEntity<MobileMoneyWithdrawResponse> initiateWithdraw(Principal principal,
                                                                      @Valid @RequestBody MobileMoneyWithdrawRequest request) {
    UUID userId = UUID.fromString(principal.getName());
    return ResponseEntity.ok(mobileMoneyService.initiateWithdraw(userId, request));
  }
}
