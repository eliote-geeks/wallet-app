package com.socialwallet.payments.mobilemoney;

import com.socialwallet.payments.mobilemoney.dto.MobileMoneyWebhookRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/mobile-money")
@RequiredArgsConstructor
public class MobileMoneyWebhookController {
  private final MobileMoneyService mobileMoneyService;

  @PostMapping
  public ResponseEntity<Void> handleWebhook(@Valid @RequestBody MobileMoneyWebhookRequest request) {
    mobileMoneyService.handleWebhook(request);
    return ResponseEntity.accepted().build();
  }
}
