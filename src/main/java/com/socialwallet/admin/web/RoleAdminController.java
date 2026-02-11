package com.socialwallet.admin.web;

import static com.socialwallet.config.RbacExpressions.ADMIN;

import com.socialwallet.admin.dto.RoleChangeRequest;
import com.socialwallet.admin.dto.UserRolesResponse;
import com.socialwallet.admin.service.RoleAdminService;
import jakarta.validation.Valid;
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

  @PostMapping("/users/{userId}/assign")
  public ResponseEntity<UserRolesResponse> assignRole(@PathVariable UUID userId,
                                                      @Valid @RequestBody RoleChangeRequest request) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignRole(userId, request.getRole())));
  }

  @PostMapping("/users/{userId}/revoke")
  public ResponseEntity<UserRolesResponse> revokeRole(@PathVariable UUID userId,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody RoleChangeRequest request) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeRole(actorUserId, userId, request.getRole())));
  }

  @PostMapping("/users/{userId}/seller")
  public ResponseEntity<UserRolesResponse> assignSeller(@PathVariable UUID userId) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignSeller(userId)));
  }

  @DeleteMapping("/users/{userId}/seller")
  public ResponseEntity<UserRolesResponse> revokeSeller(@PathVariable UUID userId) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeSeller(userId)));
  }

  @PostMapping("/users/{userId}/moderator")
  public ResponseEntity<UserRolesResponse> assignModerator(@PathVariable UUID userId) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.assignModerator(userId)));
  }

  @DeleteMapping("/users/{userId}/moderator")
  public ResponseEntity<UserRolesResponse> revokeModerator(@PathVariable UUID userId) {
    return ResponseEntity.ok(toResponse(userId, roleAdminService.revokeModerator(userId)));
  }

  private UserRolesResponse toResponse(UUID userId, Set<String> roles) {
    UserRolesResponse response = new UserRolesResponse();
    response.setUserId(userId);
    response.setRoles(roles);
    return response;
  }
}
