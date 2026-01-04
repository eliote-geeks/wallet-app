package com.socialwallet.identity.service;

import com.socialwallet.identity.IdentityException;
import com.socialwallet.identity.IdentityProperties;
import com.socialwallet.identity.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class KeycloakTokenClient {
  private final RestTemplate restTemplate;
  private final IdentityProperties properties;

  public TokenResponse passwordGrant(String username, String password) {
    MultiValueMap<String, String> form = baseForm();
    form.add("grant_type", "password");
    form.add("username", username);
    form.add("password", password);
    return tokenRequest(form);
  }

  public TokenResponse refreshToken(String refreshToken) {
    MultiValueMap<String, String> form = baseForm();
    form.add("grant_type", "refresh_token");
    form.add("refresh_token", refreshToken);
    return tokenRequest(form);
  }

  public void logout(String refreshToken) {
    MultiValueMap<String, String> form = baseForm();
    form.add("refresh_token", refreshToken);

    String url = buildRealmUrl("/protocol/openid-connect/logout");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

    try {
      restTemplate.exchange(url, HttpMethod.POST, entity, Void.class);
    } catch (HttpStatusCodeException ex) {
      throw new IdentityException(HttpStatus.UNAUTHORIZED, "Logout failed");
    }
  }

  private TokenResponse tokenRequest(MultiValueMap<String, String> form) {
    String url = buildRealmUrl("/protocol/openid-connect/token");
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
    HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(form, headers);

    try {
      ResponseEntity<TokenResponse> response = restTemplate.postForEntity(url, entity, TokenResponse.class);
      TokenResponse body = response.getBody();
      if (body == null || body.getAccessToken() == null) {
        throw new IdentityException(HttpStatus.UNAUTHORIZED, "Invalid token response");
      }
      return body;
    } catch (HttpStatusCodeException ex) {
      throw new IdentityException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
    }
  }

  private MultiValueMap<String, String> baseForm() {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    IdentityProperties.Keycloak keycloak = properties.getKeycloak();
    form.add("client_id", keycloak.getClientId());
    if (keycloak.getClientSecret() != null && !keycloak.getClientSecret().isBlank()) {
      form.add("client_secret", keycloak.getClientSecret());
    }
    return form;
  }

  private String buildRealmUrl(String path) {
    IdentityProperties.Keycloak keycloak = properties.getKeycloak();
    return keycloak.getBaseUrl() + "/realms/" + keycloak.getRealm() + path;
  }
}
