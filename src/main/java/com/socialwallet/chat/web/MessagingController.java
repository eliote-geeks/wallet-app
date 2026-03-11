package com.socialwallet.chat.web;

import com.socialwallet.chat.dto.MessagingTokenResponse;
import com.socialwallet.chat.service.MessagingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class MessagingController {
  private final MessagingService messagingService;

  @PostMapping("/token")
  public ResponseEntity<MessagingTokenResponse> token(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(messagingService.issueTokens(jwt));
  }
}
