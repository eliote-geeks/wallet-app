package com.socialwallet.chat.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.socialwallet.chat.OpenImException;
import com.socialwallet.chat.OpenImProperties;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class OpenImClient {
  private final RestTemplate restTemplate;
  private final OpenImProperties properties;

  private volatile String cachedAdminToken;
  private volatile Instant cachedAdminTokenExpiresAt;

  public String getAdminToken() {
    if (cachedAdminToken != null && cachedAdminTokenExpiresAt != null
      && Instant.now().isBefore(cachedAdminTokenExpiresAt)) {
      return cachedAdminToken;
    }
    synchronized (this) {
      if (cachedAdminToken != null && cachedAdminTokenExpiresAt != null
        && Instant.now().isBefore(cachedAdminTokenExpiresAt)) {
        return cachedAdminToken;
      }
      AdminLoginRequest request = new AdminLoginRequest();
      request.setAccount(properties.getAdminAccount());
      request.setPassword(properties.getAdminPasswordHash());
      request.setVersion(properties.getAdminVersion());
      AdminLoginResponse response = post(
        properties.getAdminBaseUrl() + "/account/login",
        request,
        null,
        new ParameterizedTypeReference<OpenImResponse<AdminLoginResponse>>() {}
      );
      cachedAdminToken = response.getAdminToken();
      cachedAdminTokenExpiresAt = Instant.now().plusSeconds(properties.getAdminTokenTtlSeconds());
      return cachedAdminToken;
    }
  }

  public RegisterResponse registerUser(RegisterRequest request) {
    return post(
      properties.getChatBaseUrl() + "/account/register",
      request,
      getAdminToken(),
      new ParameterizedTypeReference<OpenImResponse<RegisterResponse>>() {}
    );
  }

  public TokenResponse loginUser(LoginRequest request) {
    return post(
      properties.getChatBaseUrl() + "/account/login",
      request,
      null,
      new ParameterizedTypeReference<OpenImResponse<TokenResponse>>() {}
    );
  }

  public void updateUserInfo(UpdateUserRequest request) {
    post(
      properties.getChatBaseUrl() + "/user/update",
      request,
      getAdminToken(),
      new ParameterizedTypeReference<OpenImResponse<Object>>() {}
    );
  }

  private <T> T post(
    String url,
    Object payload,
    String token,
    ParameterizedTypeReference<OpenImResponse<T>> type
  ) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add("operationID", UUID.randomUUID().toString());
    if (token != null && !token.isBlank()) {
      headers.add("token", token);
    }
    HttpEntity<Object> entity = new HttpEntity<>(payload, headers);
    ResponseEntity<OpenImResponse<T>> response;
    try {
      response = restTemplate.exchange(url, HttpMethod.POST, entity, type);
    } catch (RestClientException ex) {
      throw new OpenImException(org.springframework.http.HttpStatus.BAD_GATEWAY, "OpenIM request failed: " + ex.getMessage());
    }
    OpenImResponse<T> body = response.getBody();
    if (body == null) {
      throw new OpenImException(org.springframework.http.HttpStatus.BAD_GATEWAY, "OpenIM response is empty");
    }
    if (body.getErrCode() != 0) {
      throw new OpenImException(body.getErrCode(), body.getErrMsg(), body.getErrDlt());
    }
    return body.getData();
  }

  @Data
  public static class OpenImResponse<T> {
    private int errCode;
    private String errMsg;
    private String errDlt;
    private T data;
  }

  @Data
  public static class AdminLoginRequest {
    private String account;
    private String password;
    private String version;
  }

  @Data
  public static class AdminLoginResponse {
    private String adminToken;
  }

  @Data
  public static class RegisterRequest {
    private String invitationCode;
    private String verifyCode;
    private String ip;
    private String deviceID;
    private int platform;
    private boolean autoLogin;
    private RegisterUserInfo user;
  }

  @Data
  public static class RegisterUserInfo {
    @JsonProperty("userID")
    private String userId;
    private String nickname;
    @JsonProperty("faceURL")
    private String faceUrl;
    private String areaCode;
    private String phoneNumber;
    private String email;
    private String account;
    private String password;
  }

  @Data
  public static class RegisterResponse {
    @JsonProperty("userID")
    private String userId;
    private String imToken;
    private String chatToken;
  }

  @Data
  public static class LoginRequest {
    private String account;
    private String password;
    private int platform;
    private String deviceID;
    private String ip;
    private String areaCode;
    private String phoneNumber;
    private String email;
  }

  @Data
  public static class UpdateUserRequest {
    @JsonProperty("userID")
    private String userId;
    private String account;
    private String nickname;
    @JsonProperty("faceURL")
    private String faceUrl;
    private String areaCode;
    private String phoneNumber;
    private String email;
  }

  @Data
  public static class TokenResponse {
    @JsonProperty("userID")
    private String userId;
    private String imToken;
    private String chatToken;
  }
}
