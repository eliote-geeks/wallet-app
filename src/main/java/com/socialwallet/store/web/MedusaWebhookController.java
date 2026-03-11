package com.socialwallet.store.web;

import com.socialwallet.store.service.MedusaWebhookService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/medusa")
@RequiredArgsConstructor
public class MedusaWebhookController {
  private final MedusaWebhookService webhookService;

  @PostMapping
  public ResponseEntity<Void> handle(@RequestBody String payload,
                                     @RequestHeader Map<String, String> headers) {
    webhookService.handle(payload, headers);
    return ResponseEntity.accepted().build();
  }
}
