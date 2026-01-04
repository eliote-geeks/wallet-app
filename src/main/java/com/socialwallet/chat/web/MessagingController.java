package com.socialwallet.chat.web;

import com.socialwallet.chat.dto.MessagingTokenResponse;
import com.socialwallet.chat.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
public class MessagingController {
  private final MessagingService messagingService;

  @PostMapping("/token")
  public ResponseEntity<MessagingTokenResponse> token(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(messagingService.issueTokens(jwt));
  }
}
