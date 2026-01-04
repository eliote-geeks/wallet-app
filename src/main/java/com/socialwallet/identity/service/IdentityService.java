package com.socialwallet.identity.service;

import com.socialwallet.chat.OpenImException;
import com.socialwallet.chat.service.OpenImService;
import com.socialwallet.chat.service.OpenImUserIdService;
import com.socialwallet.identity.IdentityException;
import com.socialwallet.identity.IdentityProperties;
import com.socialwallet.identity.dto.*;
import com.socialwallet.identity.model.*;
import com.socialwallet.identity.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IdentityService {
  private final UserAccountRepository userAccountRepository;
  private final OtpService otpService;
  private final KeycloakAdminClient keycloakAdminClient;
  private final KeycloakTokenClient keycloakTokenClient;
  private final IdentityProperties properties;
  private final OpenImService openImService;
  private final OpenImUserIdService openImUserIdService;

  public RegisterResponse register(RegisterRequest request) {
    String email = normalize(request.getEmail());
    String phone = normalize(request.getPhoneNumber());

    if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "Email or phone is required");
    }

    if (email != null && keycloakAdminClient.userExistsByEmail(email)) {
      throw new IdentityException(HttpStatus.CONFLICT, "Email already registered");
    }

    if (phone != null && keycloakAdminClient.userExistsByUsername(phone)) {
      throw new IdentityException(HttpStatus.CONFLICT, "Phone already registered");
    }

    OtpChannel channel = phone != null && !phone.isBlank() ? OtpChannel.PHONE : OtpChannel.EMAIL;
    String target = channel == OtpChannel.PHONE ? phone : email;

    OtpService.OtpResult result = otpService.createOtp(target, channel, OtpPurpose.REGISTER);

    RegisterResponse response = new RegisterResponse();
    response.setOtpId(result.id());
    response.setExpiresAt(result.expiresAt());
    if (properties.getOtp().isDebug()) {
      response.setDebugCode(result.code());
    }
    return response;
  }

  public TokenResponse verifyOtp(VerifyOtpRequest request) {
    OtpRequest otp = otpService.verifyOtp(request.getOtpId(), request.getCode());

    String email = otp.getChannel() == OtpChannel.EMAIL ? otp.getTarget() : null;
    String phone = otp.getChannel() == OtpChannel.PHONE ? otp.getTarget() : null;
    String username = phone != null ? phone : email;

    if (username == null) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "Missing identifier for registration");
    }

    UUID userId = keycloakAdminClient.createUser(username, email, phone, request.getPassword());

    UserAccount account = new UserAccount();
    account.setId(userId);
    account.setEmail(email);
    account.setPhoneNumber(phone);
    account.setOpenimUserId(openImUserIdService.nextId());
    account.setStatus(AccountStatus.ACTIVE);
    account.setVerifiedAt(Instant.now());
    userAccountRepository.save(account);
    try {
      openImService.ensureProvisioned(account);
    } catch (OpenImException ex) {
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "OpenIM provisioning failed: " + ex.getMessage());
    }

    return keycloakTokenClient.passwordGrant(username, request.getPassword());
  }

  public TokenResponse login(LoginRequest request) {
    String identifier = normalize(request.getIdentifier());
    if (identifier == null) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "Identifier is required");
    }

    String username = resolveUsername(identifier).orElse(identifier);
    return keycloakTokenClient.passwordGrant(username, request.getPassword());
  }

  public TokenResponse refresh(RefreshRequest request) {
    return keycloakTokenClient.refreshToken(request.getRefreshToken());
  }

  public void logout(LogoutRequest request) {
    keycloakTokenClient.logout(request.getRefreshToken());
  }

  public MeResponse me(Jwt jwt) {
    UUID userId = UUID.fromString(jwt.getSubject());
    UserAccount account = userAccountRepository.findById(userId)
      .orElseGet(() -> createFromJwt(userId, jwt));

    MeResponse response = new MeResponse();
    response.setUserId(account.getId());
    response.setEmail(account.getEmail());
    response.setPhoneNumber(account.getPhoneNumber());
    response.setStatus(account.getStatus());
    response.setCreatedAt(account.getCreatedAt());
    response.setVerifiedAt(account.getVerifiedAt());
    return response;
  }

  private UserAccount createFromJwt(UUID userId, Jwt jwt) {
    UserAccount account = new UserAccount();
    account.setId(userId);
    account.setEmail(jwt.getClaimAsString("email"));
    account.setPhoneNumber(jwt.getClaimAsString("phone_number"));
    account.setOpenimUserId(openImUserIdService.nextId());
    account.setStatus(AccountStatus.ACTIVE);
    account.setVerifiedAt(Instant.now());
    return userAccountRepository.save(account);
  }

  private Optional<String> resolveUsername(String identifier) {
    if (identifier == null) {
      return Optional.empty();
    }
    if (identifier.contains("@")) {
      return userAccountRepository.findByEmailIgnoreCase(identifier)
        .map(account -> account.getPhoneNumber() != null ? account.getPhoneNumber() : identifier);
    }
    return Optional.of(identifier);
  }

  private String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
