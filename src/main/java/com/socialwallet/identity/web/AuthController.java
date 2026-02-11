package com.socialwallet.identity.web;

import com.socialwallet.identity.dto.*;
import com.socialwallet.identity.service.IdentityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final IdentityService identityService;

  @PostMapping("/register")
  @Operation(summary = "Register a new account", description = "Creates an OTP request for email or phone verification.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "OTP request created"),
    @ApiResponse(responseCode = "400", description = "Invalid payload"),
    @ApiResponse(responseCode = "409", description = "Email or phone already registered")
  })
  public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
    return ResponseEntity.ok(identityService.register(request));
  }

  @PostMapping("/verify-otp")
  @Operation(summary = "Verify OTP", description = "Verifies OTP and creates the Keycloak user.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "OTP verified, tokens returned"),
    @ApiResponse(responseCode = "400", description = "Invalid or expired OTP")
  })
  public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
    return ResponseEntity.ok(identityService.verifyOtp(request));
  }

  @PostMapping("/login")
  @Operation(summary = "Login", description = "Returns Keycloak access and refresh tokens.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Authenticated"),
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
  })
  public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
    return ResponseEntity.ok(identityService.login(request));
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh token", description = "Refreshes the access token.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Token refreshed"),
    @ApiResponse(responseCode = "401", description = "Invalid refresh token")
  })
  public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(identityService.refresh(request));
  }

  @PostMapping("/logout")
  @Operation(summary = "Logout", description = "Revokes the refresh token.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "204", description = "Logged out")
  })
  public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
    identityService.logout(request);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/me")
  @PreAuthorize(PLATFORM_USER)
  @Operation(summary = "Get current account", description = "Returns the current authenticated user info.")
  @ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Account details"),
    @ApiResponse(responseCode = "401", description = "Unauthenticated")
  })
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok(identityService.me(jwt));
  }
}
