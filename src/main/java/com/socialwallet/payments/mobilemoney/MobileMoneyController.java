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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/payments/mobile-money")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
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
