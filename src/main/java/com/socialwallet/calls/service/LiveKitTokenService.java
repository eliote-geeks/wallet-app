package com.socialwallet.calls.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.socialwallet.calls.CallException;
import com.socialwallet.calls.LiveKitProperties;
import com.socialwallet.calls.dto.CallTokenRequest;
import com.socialwallet.calls.dto.CallTokenResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LiveKitTokenService {
  private static final int MIN_SECRET_BYTES = 32;

  private final LiveKitProperties properties;

  public CallTokenResponse issueToken(Jwt jwt, CallTokenRequest request) {
    ensureEnabled();
    String identity = jwt.getSubject();
    String room = resolveRoom(request.getRoomName());
    String token = signToken(identity, resolveName(jwt), room, request.isAudioOnly());

    CallTokenResponse response = new CallTokenResponse();
    response.setRoomName(room);
    response.setIdentity(identity);
    response.setToken(token);
    response.setLivekitUrl(properties.getUrl());
    return response;
  }

  private void ensureEnabled() {
    if (!properties.isEnabled()) {
      throw new CallException(HttpStatus.SERVICE_UNAVAILABLE, "LiveKit integration is disabled");
    }
    String secret = properties.getApiSecret();
    if (secret == null || secret.isBlank()) {
      throw new CallException(HttpStatus.BAD_REQUEST, "LIVEKIT_API_SECRET is required");
    }
    byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MIN_SECRET_BYTES) {
      throw new CallException(HttpStatus.BAD_REQUEST, "LIVEKIT_API_SECRET must be at least 32 chars");
    }
  }

  private String resolveRoom(String roomName) {
    if (roomName == null || roomName.isBlank()) {
      return "call-" + UUID.randomUUID();
    }
    return roomName.trim();
  }

  private String resolveName(Jwt jwt) {
    String name = jwt.getClaimAsString("name");
    if (name != null && !name.isBlank()) {
      return name;
    }
    String phone = jwt.getClaimAsString("phone_number");
    if (phone != null && !phone.isBlank()) {
      return phone;
    }
    return jwt.getSubject();
  }

  private String signToken(String identity, String name, String room, boolean audioOnly) {
    Instant now = Instant.now();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
      .issuer(properties.getApiKey())
      .subject(identity)
      .issueTime(Date.from(now))
      .expirationTime(Date.from(now.plusSeconds(properties.getTokenTtlSeconds())))
      .claim("name", name)
      .claim("video", buildVideoGrant(room, audioOnly))
      .build();

    try {
      SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      signedJWT.sign(new MACSigner(properties.getApiSecret()));
      return signedJWT.serialize();
    } catch (JOSEException ex) {
      throw new CallException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to sign LiveKit token");
    }
  }

  private Map<String, Object> buildVideoGrant(String room, boolean audioOnly) {
    Map<String, Object> grant = new LinkedHashMap<>();
    grant.put("room", room);
    grant.put("roomJoin", true);
    grant.put("canPublish", true);
    grant.put("canSubscribe", true);
    grant.put("canPublishData", true);
    if (audioOnly) {
      grant.put("canPublishSources", List.of("microphone"));
    }
    return grant;
  }

}
