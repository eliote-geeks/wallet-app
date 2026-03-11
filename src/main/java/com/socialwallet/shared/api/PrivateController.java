package com.socialwallet.shared.api;

import java.util.Map;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/private")
@PreAuthorize(PLATFORM_USER)
public class PrivateController {
  @GetMapping("/me")
  public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
    return Map.of("sub", jwt.getSubject());
  }
}
