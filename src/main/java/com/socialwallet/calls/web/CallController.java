package com.socialwallet.calls.web;

import com.socialwallet.calls.dto.CallInviteRequest;
import com.socialwallet.calls.dto.CallInviteResponse;
import com.socialwallet.calls.dto.CallHistoryItemResponse;
import com.socialwallet.calls.dto.CallSignalRequest;
import com.socialwallet.calls.dto.CallSignalResponse;
import com.socialwallet.calls.dto.CallTokenRequest;
import com.socialwallet.calls.dto.CallTokenResponse;
import com.socialwallet.calls.service.CallHistoryService;
import com.socialwallet.calls.service.CallInviteService;
import com.socialwallet.calls.service.LiveKitTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final CallHistoryService callHistoryService;

  @PostMapping("/token")
  @Operation(summary = "Create LiveKit token", description = "Creates a token to join or create a call room.")
  public ResponseEntity<CallTokenResponse> token(@AuthenticationPrincipal Jwt jwt,
                                                 @Valid @RequestBody CallTokenRequest request) {
    return ResponseEntity.ok(tokenService.issueToken(jwt, request));
  }

  @PostMapping("/invite")
  @Operation(summary = "Send call invite", description = "Sends a call invitation via OpenIM and stores call history.")
  public ResponseEntity<CallInviteResponse> invite(@AuthenticationPrincipal Jwt jwt,
                                                   @Valid @RequestBody CallInviteRequest request) {
    return ResponseEntity.ok(callInviteService.sendInvite(jwt, request));
  }

  @PostMapping("/respond")
  @Operation(summary = "Send call response", description = "Sends call response via OpenIM and updates call history.")
  public ResponseEntity<CallSignalResponse> respond(@AuthenticationPrincipal Jwt jwt,
                                                    @Valid @RequestBody CallSignalRequest request) {
    return ResponseEntity.ok(callInviteService.sendResponse(jwt, request));
  }

  @GetMapping("/history")
  @Operation(summary = "Get call history", description = "Returns latest calls for current user.")
  public ResponseEntity<List<CallHistoryItemResponse>> history(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(callHistoryService.findForUser(UUID.fromString(jwt.getSubject())));
  }
}
