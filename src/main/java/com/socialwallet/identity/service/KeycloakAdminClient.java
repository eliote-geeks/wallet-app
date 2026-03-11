package com.socialwallet.identity.service;

import com.socialwallet.identity.IdentityException;
import com.socialwallet.identity.IdentityProperties;
import com.socialwallet.identity.dto.TokenResponse;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
public class KeycloakAdminClient {
  private final RestTemplate restTemplate;
  private final IdentityProperties properties;
  private final Object tokenLock = new Object();
  private AdminToken adminToken;

  public boolean userExistsByEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    String url = adminBaseUrl() + "/users";
    URI uri = UriComponentsBuilder.fromHttpUrl(url)
      .queryParam("email", email)
      .queryParam("max", 1)
      .build()
      .toUri();

    ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
      uri,
      HttpMethod.GET,
      new HttpEntity<>(adminHeaders()),
      new ParameterizedTypeReference<List<Map<String, Object>>>() {}
    );

    List<Map<String, Object>> body = response.getBody();
    return body != null && !body.isEmpty();
  }

  public boolean userExistsByUsername(String username) {
    if (username == null || username.isBlank()) {
      return false;
    }
    String url = adminBaseUrl() + "/users";
    URI uri = UriComponentsBuilder.fromHttpUrl(url)
      .queryParam("username", username)
      .queryParam("exact", true)
      .queryParam("max", 1)
      .build()
      .toUri();

    ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
      uri,
      HttpMethod.GET,
      new HttpEntity<>(adminHeaders()),
      new ParameterizedTypeReference<List<Map<String, Object>>>() {}
    );

    List<Map<String, Object>> body = response.getBody();
    return body != null && !body.isEmpty();
  }

  public UUID createUser(String username, String email, String phoneNumber, String password) {
    String userId = createUserInternal(username, email, phoneNumber);
    resetPassword(userId, password);
    assignDefaultRole(userId);
    return UUID.fromString(userId);
  }

  public Set<String> getUserRealmRoles(UUID userId) {
    try {
      ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
        adminBaseUrl() + "/users/" + userId + "/role-mappings/realm",
        HttpMethod.GET,
        new HttpEntity<>(adminHeaders()),
        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
      );
      return extractRoleNames(response.getBody());
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new IdentityException(HttpStatus.NOT_FOUND, "User not found in Keycloak");
      }
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak user roles lookup failed");
    }
  }

  public Set<String> assignRealmRoles(UUID userId, Collection<String> roleNames) {
    List<Map<String, Object>> resolvedRoles = resolveRoles(roleNames, true);
    if (resolvedRoles.isEmpty()) {
      return getUserRealmRoles(userId);
    }

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    try {
      restTemplate.exchange(
        adminBaseUrl() + "/users/" + userId + "/role-mappings/realm",
        HttpMethod.POST,
        new HttpEntity<>(resolvedRoles, headers),
        Void.class
      );
      return getUserRealmRoles(userId);
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new IdentityException(HttpStatus.NOT_FOUND, "User not found in Keycloak");
      }
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak role assignment failed");
    }
  }

  public Set<String> removeRealmRoles(UUID userId, Collection<String> roleNames) {
    List<Map<String, Object>> resolvedRoles = resolveRoles(roleNames, false);
    if (resolvedRoles.isEmpty()) {
      return getUserRealmRoles(userId);
    }

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    try {
      restTemplate.exchange(
        adminBaseUrl() + "/users/" + userId + "/role-mappings/realm",
        HttpMethod.DELETE,
        new HttpEntity<>(resolvedRoles, headers),
        Void.class
      );
      return getUserRealmRoles(userId);
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
        throw new IdentityException(HttpStatus.NOT_FOUND, "User not found in Keycloak");
      }
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak role removal failed");
    }
  }

  private String createUserInternal(String username, String email, String phoneNumber) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("username", username);
    payload.put("enabled", true);

    if (email != null && !email.isBlank()) {
      payload.put("email", email);
      payload.put("emailVerified", true);
    }

    if (phoneNumber != null && !phoneNumber.isBlank()) {
      Map<String, List<String>> attributes = new HashMap<>();
      attributes.put("phone_number", List.of(phoneNumber));
      payload.put("attributes", attributes);
    }

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    ResponseEntity<Void> response = restTemplate.postForEntity(
      adminBaseUrl() + "/users",
      new HttpEntity<>(payload, headers),
      Void.class
    );

    if (response.getStatusCode() != HttpStatus.CREATED) {
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak user creation failed");
    }

    String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
    if (location == null || !location.contains("/")) {
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak user id not returned");
    }
    return location.substring(location.lastIndexOf('/') + 1);
  }

  private void resetPassword(String userId, String password) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("type", "password");
    payload.put("value", password);
    payload.put("temporary", false);

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    restTemplate.exchange(
      adminBaseUrl() + "/users/" + userId + "/reset-password",
      HttpMethod.PUT,
      new HttpEntity<>(payload, headers),
      Void.class
    );
  }

  private void assignDefaultRole(String userId) {
    String roleName = properties.getKeycloak().getDefaultRole();
    if (roleName == null || roleName.isBlank()) {
      return;
    }

    Map<String, Object> role = getRole(roleName);
    if (role == null) {
      createRole(roleName);
      role = getRole(roleName);
    }

    if (role == null) {
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Unable to resolve default role");
    }

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    restTemplate.exchange(
      adminBaseUrl() + "/users/" + userId + "/role-mappings/realm",
      HttpMethod.POST,
      new HttpEntity<>(List.of(role), headers),
      Void.class
    );
  }

  private Map<String, Object> getRole(String roleName) {
    try {
      ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
        adminBaseUrl() + "/roles/" + roleName,
        HttpMethod.GET,
        new HttpEntity<>(adminHeaders()),
        new ParameterizedTypeReference<Map<String, Object>>() {}
      );
      return response.getBody();
    } catch (HttpStatusCodeException ex) {
      if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
        return null;
      }
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak role lookup failed");
    }
  }

  private void createRole(String roleName) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("name", roleName);

    HttpHeaders headers = adminHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    restTemplate.exchange(
      adminBaseUrl() + "/roles",
      HttpMethod.POST,
      new HttpEntity<>(payload, headers),
      Void.class
    );
  }

  private String adminBaseUrl() {
    IdentityProperties.Keycloak keycloak = properties.getKeycloak();
    return keycloak.getBaseUrl() + "/admin/realms/" + keycloak.getRealm();
  }

  private HttpHeaders adminHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(getAdminToken());
    return headers;
  }

  private String getAdminToken() {
    AdminToken token = adminToken;
    if (token != null && token.isValid()) {
      return token.value();
    }
    synchronized (tokenLock) {
      token = adminToken;
      if (token != null && token.isValid()) {
        return token.value();
      }
      AdminToken refreshed = fetchAdminToken();
      adminToken = refreshed;
      return refreshed.value();
    }
  }

  private AdminToken fetchAdminToken() {
    IdentityProperties.Keycloak keycloak = properties.getKeycloak();

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", "password");
    form.add("client_id", keycloak.getAdminClientId());
    form.add("username", keycloak.getAdminUsername());
    form.add("password", keycloak.getAdminPassword());

    String url = keycloak.getBaseUrl() + "/realms/" + keycloak.getAdminRealm() + "/protocol/openid-connect/token";
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    try {
      ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
        url,
        new HttpEntity<>(form, headers),
        TokenResponse.class
      );
      TokenResponse token = response.getBody();
      if (token == null || token.getAccessToken() == null) {
        throw new IdentityException(HttpStatus.BAD_GATEWAY, "Unable to authenticate Keycloak admin");
      }
      long expiresIn = Math.max(30, token.getExpiresIn());
      return new AdminToken(token.getAccessToken(), Instant.now().plusSeconds(expiresIn - 10));
    } catch (HttpStatusCodeException ex) {
      throw new IdentityException(HttpStatus.BAD_GATEWAY, "Keycloak admin auth failed");
    }
  }

  private record AdminToken(String value, Instant expiresAt) {
    boolean isValid() {
      return expiresAt != null && expiresAt.isAfter(Instant.now());
    }
  }

  private List<Map<String, Object>> resolveRoles(Collection<String> roleNames, boolean createIfMissing) {
    Set<String> normalizedRoleNames = normalizeRoleNames(roleNames);
    List<Map<String, Object>> roles = new java.util.ArrayList<>();
    for (String roleName : normalizedRoleNames) {
      Map<String, Object> role = getRole(roleName);
      if (role == null && createIfMissing) {
        createRole(roleName);
        role = getRole(roleName);
      }
      if (role != null) {
        roles.add(role);
      }
    }
    return roles;
  }

  private Set<String> normalizeRoleNames(Collection<String> roleNames) {
    Set<String> normalized = new LinkedHashSet<>();
    if (roleNames == null) {
      return normalized;
    }
    for (String roleName : roleNames) {
      if (roleName == null) {
        continue;
      }
      String trimmed = roleName.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      normalized.add(trimmed.toUpperCase(Locale.ROOT));
    }
    return normalized;
  }

  private Set<String> extractRoleNames(List<Map<String, Object>> roles) {
    Set<String> names = new LinkedHashSet<>();
    if (roles == null) {
      return names;
    }
    for (Map<String, Object> role : roles) {
      if (role == null) {
        continue;
      }
      Object name = role.get("name");
      if (name == null) {
        continue;
      }
      String roleName = name.toString().trim();
      if (!roleName.isEmpty()) {
        names.add(roleName.toUpperCase(Locale.ROOT));
      }
    }
    return names;
  }
}
