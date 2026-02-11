package com.socialwallet.admin.service;

import com.socialwallet.admin.model.RoleAuditLog;
import com.socialwallet.admin.repository.RoleAuditLogRepository;
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
  private static final String ACTION_ASSIGN = "ASSIGN";
  private static final String ACTION_REVOKE = "REVOKE";

  private final UserAccountRepository userAccountRepository;
  private final KeycloakAdminClient keycloakAdminClient;
  private final RoleAuditLogRepository roleAuditLogRepository;

  public Set<String> getRoles(UUID userId) {
    assertUserExists(userId);
    return keycloakAdminClient.getUserRealmRoles(userId);
  }

  public Set<String> assignRole(UUID actorUserId, UUID userId, String roleName) {
    assertUserExists(userId);
    String normalized = normalizeRole(roleName);
    Set<String> roles = keycloakAdminClient.assignRealmRoles(userId, List.of(normalized));
    audit(actorUserId, userId, normalized, ACTION_ASSIGN);
    return roles;
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
    Set<String> roles = keycloakAdminClient.removeRealmRoles(targetUserId, List.of(normalized));
    audit(actorUserId, targetUserId, normalized, ACTION_REVOKE);
    return roles;
  }

  public Set<String> assignSeller(UUID actorUserId, UUID userId) {
    assertUserExists(userId);
    Set<String> roles = keycloakAdminClient.assignRealmRoles(userId, List.of(ROLE_USER, ROLE_SELLER));
    audit(actorUserId, userId, ROLE_SELLER, ACTION_ASSIGN);
    return roles;
  }

  public Set<String> revokeSeller(UUID actorUserId, UUID userId) {
    assertUserExists(userId);
    Set<String> roles = keycloakAdminClient.removeRealmRoles(userId, List.of(ROLE_SELLER));
    audit(actorUserId, userId, ROLE_SELLER, ACTION_REVOKE);
    return roles;
  }

  public Set<String> assignModerator(UUID actorUserId, UUID userId) {
    assertUserExists(userId);
    Set<String> roles = keycloakAdminClient.assignRealmRoles(userId, List.of(ROLE_USER, ROLE_MODERATOR));
    audit(actorUserId, userId, ROLE_MODERATOR, ACTION_ASSIGN);
    return roles;
  }

  public Set<String> revokeModerator(UUID actorUserId, UUID userId) {
    assertUserExists(userId);
    Set<String> roles = keycloakAdminClient.removeRealmRoles(userId, List.of(ROLE_MODERATOR));
    audit(actorUserId, userId, ROLE_MODERATOR, ACTION_REVOKE);
    return roles;
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

  private void audit(UUID actorUserId, UUID targetUserId, String roleName, String action) {
    RoleAuditLog auditLog = new RoleAuditLog();
    auditLog.setActorUserId(actorUserId);
    auditLog.setTargetUserId(targetUserId);
    auditLog.setRoleName(roleName);
    auditLog.setAction(action);
    roleAuditLogRepository.save(auditLog);
  }
}
