package com.socialwallet.admin.web;

import static com.socialwallet.config.RbacExpressions.ADMIN;

import com.socialwallet.admin.dto.RoleAuditLogDto;
import com.socialwallet.admin.dto.RoleChangeRequest;
import com.socialwallet.admin.dto.UserRolesResponse;
import com.socialwallet.admin.model.RoleAuditLog;
import com.socialwallet.admin.service.RoleAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@PreAuthorize(ADMIN)
public class RoleAdminController {
  private final RoleAdminService roleAdminService;

  @GetMapping("/users/{userId}")
  public ResponseEntity<UserRolesResponse> getUserRoles(@PathVariable UUID userId) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.getRoles(userId)));
  }

  @GetMapping("/audit")
  public ResponseEntity<Page<RoleAuditLogDto>> listAudit(
    @RequestParam(name = "actorUserId", required = false) UUID actorUserId,
    @RequestParam(name = "targetUserId", required = false) UUID targetUserId,
    @RequestParam(name = "role", required = false) String role,
    @RequestParam(name = "action", required = false) String action,
    @PageableDefault(size = 50, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
    Pageable pageable) {
    Page<RoleAuditLogDto> page = roleAdminService
      .listAudit(actorUserId, targetUserId, role, action, pageable)
      .map(this::toAuditResponse);
    return ResponseEntity.ok(page);
  }

  @PostMapping("/users/{userId}/assign")
  public ResponseEntity<UserRolesResponse> assignRole(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody RoleChangeRequest request) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignRole(actorUserId, userId, request.getRole())));
  }

  @PostMapping("/users/{userId}/revoke")
  public ResponseEntity<UserRolesResponse> revokeRole(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody RoleChangeRequest request) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeRole(actorUserId, userId, request.getRole())));
  }

  @PostMapping("/users/{userId}/seller")
  public ResponseEntity<UserRolesResponse> assignSeller(@PathVariable UUID userId,
                                                        @AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignSeller(actorUserId, userId)));
  }

  @DeleteMapping("/users/{userId}/seller")
  public ResponseEntity<UserRolesResponse> revokeSeller(@PathVariable UUID userId,
                                                        @AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeSeller(actorUserId, userId)));
  }

  @PostMapping("/users/{userId}/moderator")
  public ResponseEntity<UserRolesResponse> assignModerator(@PathVariable UUID userId,
                                                           @AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignModerator(actorUserId, userId)));
  }

  @DeleteMapping("/users/{userId}/moderator")
  public ResponseEntity<UserRolesResponse> revokeModerator(@PathVariable UUID userId,
                                                           @AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeModerator(actorUserId, userId)));
  }

  private UserRolesResponse toResponse(UUID userId, Set<String> roles) {
    UserRolesResponse response = new UserRolesResponse();
    response.setUserId(userId);
    response.setRoles(roles);
    return response;
  }

  private RoleAuditLogDto toAuditResponse(RoleAuditLog entry) {
    RoleAuditLogDto dto = new RoleAuditLogDto();
    dto.setId(entry.getId());
    dto.setActorUserId(entry.getActorUserId());
    dto.setTargetUserId(entry.getTargetUserId());
    dto.setRoleName(entry.getRoleName());
    dto.setAction(entry.getAction());
    dto.setCreatedAt(entry.getCreatedAt());
    return dto;
  }
}
