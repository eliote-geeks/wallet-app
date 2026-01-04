package com.socialwallet.identity.service;

import com.socialwallet.identity.IdentityException;
import com.socialwallet.identity.IdentityProperties;
import com.socialwallet.identity.model.OtpChannel;
import com.socialwallet.identity.model.OtpPurpose;
import com.socialwallet.identity.model.OtpRequest;
import com.socialwallet.identity.repository.OtpRequestRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {
  private final OtpRequestRepository otpRepository;
  private final PasswordEncoder passwordEncoder;
  private final IdentityProperties properties;
  private final SecureRandom random = new SecureRandom();

  public OtpResult createOtp(String target, OtpChannel channel, OtpPurpose purpose) {
    String code = generateCode();

    OtpRequest request = new OtpRequest();
    request.setTarget(target);
    request.setChannel(channel);
    request.setPurpose(purpose);
    request.setCodeHash(passwordEncoder.encode(code));
    request.setAttempts(0);
    request.setMaxAttempts(properties.getOtp().getMaxAttempts());
    request.setExpiresAt(Instant.now().plus(properties.getOtp().getTtlSeconds(), ChronoUnit.SECONDS));

    otpRepository.save(request);

    if (properties.getOtp().isDebug()) {
      log.info("OTP debug code for {}: {}", target, code);
    }

    return new OtpResult(request.getId(), request.getExpiresAt(), code);
  }

  public OtpRequest verifyOtp(UUID otpId, String code) {
    OtpRequest request = otpRepository.findById(otpId)
      .orElseThrow(() -> new IdentityException(HttpStatus.NOT_FOUND, "OTP not found"));

    if (request.isConsumed()) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "OTP already used");
    }

    Instant now = Instant.now();
    if (request.isExpired(now)) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "OTP expired");
    }

    if (request.getAttempts() >= request.getMaxAttempts()) {
      throw new IdentityException(HttpStatus.TOO_MANY_REQUESTS, "OTP attempts exceeded");
    }

    if (!passwordEncoder.matches(code, request.getCodeHash())) {
      request.setAttempts(request.getAttempts() + 1);
      otpRepository.save(request);
      throw new IdentityException(HttpStatus.BAD_REQUEST, "Invalid OTP code");
    }

    request.setConsumedAt(now);
    otpRepository.save(request);
    return request;
  }

  private String generateCode() {
    int value = random.nextInt(1_000_000);
    return String.format("%06d", value);
  }

  public record OtpResult(UUID id, Instant expiresAt, String code) {}
}
