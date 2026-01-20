package com.socialwallet.calls.web;

import com.socialwallet.calls.dto.CallInviteRequest;
import com.socialwallet.calls.dto.CallInviteResponse;
import com.socialwallet.calls.dto.CallSignalRequest;
import com.socialwallet.calls.dto.CallSignalResponse;
import com.socialwallet.calls.dto.CallTokenRequest;
import com.socialwallet.calls.dto.CallTokenResponse;
import com.socialwallet.calls.service.CallInviteService;
import com.socialwallet.calls.service.LiveKitTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {
  private final LiveKitTokenService tokenService;
  private final CallInviteService callInviteService;

  @PostMapping("/token")
  @Operation(summary = "Create LiveKit token", description = "Creates a token to join or create a call room.")
  public ResponseEntity<CallTokenResponse> token(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CallTokenRequest request) {
    return ResponseEntity.ok(tokenService.issueToken(jwt, request));
  }

  @PostMapping("/invite")
  @Operation(summary = "Create call invite payload", description = "Builds the OpenIM payload for a call invite.")
  public ResponseEntity<CallInviteResponse> invite(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CallInviteRequest request) {
    return ResponseEntity.ok(callInviteService.buildInvite(jwt, request));
  }

  @PostMapping("/respond")
  @Operation(summary = "Create call response payload", description = "Builds the OpenIM payload for a call response.")
  public ResponseEntity<CallSignalResponse> respond(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CallSignalRequest request) {
    return ResponseEntity.ok(callInviteService.buildResponse(jwt, request));
  }
}
