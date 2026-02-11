package com.socialwallet.admin.service;

import com.socialwallet.identity.IdentityException;
import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.identity.service.KeycloakAdminClient;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleAdminService {
  private static final String ROLE_USER = "USER";
  private static final String ROLE_SELLER = "SELLER";
  private static final String ROLE_MODERATOR = "MODERATOR";
  private static final String ROLE_ADMIN = "ADMIN";

  private final UserAccountRepository userAccountRepository;
  private final KeycloakAdminClient keycloakAdminClient;

  public Set<String> getRoles(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.getUserRealmRoles(userId);
  }

  public Set<String> assignRole(UUID userId, String roleName) {
    assertUserExists(userId);
    String normalized = normalizeRole(roleName);
    return keycloakAdminClient.assignRealmRoles(userId, List.of(normalized));
  }

  public Set<String> revokeRole(UUID actorUserId, UUID targetUserId, String roleName) {
    assertUserExists(targetUserId);
    String normalized = normalizeRole(roleName);
    if (ROLE_USER.equals(normalized)) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "USER role cannot be revoked");
    }
    if (ROLE_ADMIN.equals(normalized) && actorUserId != null && actorUserId.equals(targetUserId)) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "You cannot revoke your own ADMIN role");
    }
    return keycloakAdminClient.removeRealmRoles(targetUserId, List.of(normalized));
  }

  public Set<String> assignSeller(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.assignRealmRoles(userId, List.of(ROLE_USER, ROLE_SELLER));
  }

  public Set<String> revokeSeller(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.removeRealmRoles(userId, List.of(ROLE_SELLER));
  }

  public Set<String> assignModerator(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.assignRealmRoles(userId, List.of(ROLE_USER, ROLE_MODERATOR));
  }

  public Set<String> revokeModerator(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.removeRealmRoles(userId, List.of(ROLE_MODERATOR));
  }

  private void assertUserExists(UUID userId) {
    if (userId == null || !userAccountRepository.existsById(userId)) {
      throw new IdentityException(HttpStatus.NOT_FOUND, "User not found");
    }
  }

  private String normalizeRole(String roleName) {
    if (roleName == null || roleName.trim().isEmpty()) {
      throw new IdentityException(HttpStatus.BAD_REQUEST, "Role is required");
    }
    return roleName.trim().toUpperCase(Locale.ROOT);
  }
}
