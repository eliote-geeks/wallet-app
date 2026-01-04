package com.socialwallet.chat.service;

import com.socialwallet.chat.OpenImException;
import com.socialwallet.chat.OpenImProperties;
import com.socialwallet.chat.dto.MessagingTokenResponse;
import com.socialwallet.identity.model.UserAccount;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

@Service
@RequiredArgsConstructor
public class OpenImService {
  private static final int ACCOUNT_NOT_FOUND = 20002;
  private static final Set<Integer> REGISTER_CONFLICT_CODES = Set.of(20003, 20004, 20014);

  private final OpenImClient client;
  private final OpenImProperties properties;

  public boolean isEnabled() {
    return properties.isEnabled();
  }

  public void ensureProvisioned(UserAccount account) {
    if (!properties.isEnabled()) {
      return;
    }
    if (account.getOpenimUserId() == null) {
      throw new OpenImException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenIM user id is missing");
    }
    OpenImClient.RegisterRequest request = buildRegisterRequest(account, false);
    try {
      client.registerUser(request);
    } catch (OpenImException ex) {
      if (!REGISTER_CONFLICT_CODES.contains(ex.getErrCode())) {
        throw ex;
      }
    }
  }

  public MessagingTokenResponse issueTokens(UserAccount account) {
    if (!properties.isEnabled()) {
      throw new OpenImException(HttpStatus.SERVICE_UNAVAILABLE, "OpenIM integration is disabled");
    }
    if (account.getOpenimUserId() == null) {
      throw new OpenImException(HttpStatus.INTERNAL_SERVER_ERROR, "OpenIM user id is missing");
    }
    OpenImClient.TokenResponse token;
    try {
      token = client.loginUser(buildLoginRequest(account));
    } catch (OpenImException ex) {
      if (ex.getErrCode() != ACCOUNT_NOT_FOUND) {
        throw ex;
      }
      ensureProvisioned(account);
      token = client.loginUser(buildLoginRequest(account));
    }
    MessagingTokenResponse response = new MessagingTokenResponse();
    response.setUserId(account.getId().toString());
    response.setOpenimUserId(token.getUserId());
    response.setImToken(token.getImToken());
    response.setChatToken(token.getChatToken());
    return response;
  }

  private OpenImClient.RegisterRequest buildRegisterRequest(UserAccount account, boolean autoLogin) {
    OpenImClient.RegisterUserInfo userInfo = new OpenImClient.RegisterUserInfo();
    String openImUserId = String.valueOf(account.getOpenimUserId());
    userInfo.setUserId(openImUserId);
    userInfo.setNickname(resolveNickname(account));
    userInfo.setAccount(deriveAccount(account.getOpenimUserId()));
    userInfo.setPassword(derivePassword(account.getOpenimUserId()));
    userInfo.setEmail(account.getEmail());
    userInfo.setAreaCode(properties.getDefaultAreaCode());
    userInfo.setPhoneNumber(openImUserId);

    OpenImClient.RegisterRequest request = new OpenImClient.RegisterRequest();
    request.setVerifyCode(properties.getSuperCode());
    request.setDeviceID(properties.getDeviceId());
    request.setPlatform(properties.getPlatform());
    request.setAutoLogin(autoLogin);
    request.setUser(userInfo);
    return request;
  }

  private OpenImClient.LoginRequest buildLoginRequest(UserAccount account) {
    OpenImClient.LoginRequest request = new OpenImClient.LoginRequest();
    request.setAccount(deriveAccount(account.getOpenimUserId()));
    request.setPassword(derivePassword(account.getOpenimUserId()));
    request.setPlatform(properties.getPlatform());
    request.setDeviceID(properties.getDeviceId());
    request.setAreaCode(properties.getDefaultAreaCode());
    request.setPhoneNumber(String.valueOf(account.getOpenimUserId()));
    return request;
  }

  private String deriveAccount(Long openImUserId) {
    return String.valueOf(openImUserId);
  }

  private String derivePassword(Long openImUserId) {
    String raw = openImUserId + ":" + properties.getPasswordSalt();
    return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
  }

  private String resolveNickname(UserAccount account) {
    String email = account.getEmail();
    if (email != null && email.contains("@")) {
      return email.substring(0, email.indexOf('@'));
    }
    String phone = account.getPhoneNumber();
    if (phone != null && !phone.isBlank()) {
      return phone;
    }
    String id = account.getId().toString();
    return "user-" + id.substring(0, Math.min(id.length(), 8));
  }

  private PhoneParts resolvePhone(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    if (trimmed.contains(" ")) {
      String[] parts = trimmed.split("\\s+", 2);
      String area = normalizeAreaCode(parts[0]);
      String number = digitsOnly(parts[1]);
      if (!number.isEmpty()) {
        return new PhoneParts(area, number);
      }
    }
    String number = digitsOnly(trimmed);
    if (number.isEmpty()) {
      return null;
    }
    String defaultDigits = digitsOnly(properties.getDefaultAreaCode());
    if (trimmed.startsWith("+") && !defaultDigits.isEmpty()
      && number.startsWith(defaultDigits) && number.length() > defaultDigits.length()) {
      return new PhoneParts(normalizeAreaCode(defaultDigits), number.substring(defaultDigits.length()));
    }
    return new PhoneParts(normalizeAreaCode(properties.getDefaultAreaCode()), number);
  }

  private String normalizeAreaCode(String areaCode) {
    if (areaCode == null || areaCode.isBlank()) {
      return properties.getDefaultAreaCode();
    }
    String trimmed = areaCode.trim();
    return trimmed.startsWith("+") ? trimmed : "+" + trimmed;
  }

  private String digitsOnly(String value) {
    return value.replaceAll("\\D", "");
  }

  private record PhoneParts(String areaCode, String number) {}
}
