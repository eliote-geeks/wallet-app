package com.socialwallet.admin.web;

import static com.socialwallet.config.RbacExpressions.ADMIN;

import com.socialwallet.admin.dto.StoreSellerDecisionRequest;
import com.socialwallet.store.dto.StoreSellerApplicationResponse;
import com.socialwallet.store.model.StoreSellerApplication;
import com.socialwallet.store.model.StoreSellerApplicationStatus;
import com.socialwallet.store.service.StoreSellerAdminService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/store/sellers")
@RequiredArgsConstructor
@PreAuthorize(ADMIN)
public class StoreSellerAdminController {
  private final StoreSellerAdminService adminService;

  @GetMapping("/applications")
  public ResponseEntity<Page<StoreSellerApplicationResponse>> listApplications(
      @RequestParam(name = "status", required = false) StoreSellerApplicationStatus status,
      Pageable pageable) {
    return ResponseEntity.ok(adminService.listApplications(status, pageable).map(this::toResponse));
  }

  @GetMapping("/applications/{id}")
  public ResponseEntity<StoreSellerApplicationResponse> getApplication(@PathVariable UUID id) {
    return ResponseEntity.ok(toResponse(adminService.getApplication(id)));
  }

  @PostMapping("/applications/{id}/approve")
  public ResponseEntity<StoreSellerApplicationResponse> approve(@PathVariable UUID id,
                                                                @AuthenticationPrincipal Jwt jwt) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    StoreSellerApplication approved = adminService.approve(id, actorUserId);
    return ResponseEntity.ok(toResponse(approved));
  }

  @PostMapping("/applications/{id}/reject")
  public ResponseEntity<StoreSellerApplicationResponse> reject(@PathVariable UUID id,
                                                               @AuthenticationPrincipal Jwt jwt,
                                                               @Valid @RequestBody StoreSellerDecisionRequest request) {
    UUID actorUserId = jwt != null ? UUID.fromString(jwt.getSubject()) : null;
    StoreSellerApplication rejected = adminService.reject(id, actorUserId, request.getRejectionReason());
    return ResponseEntity.ok(toResponse(rejected));
  }

  private StoreSellerApplicationResponse toResponse(StoreSellerApplication app) {
    StoreSellerApplicationResponse dto = new StoreSellerApplicationResponse();
    dto.setId(app.getId());
    dto.setUserId(app.getUserId());
    dto.setShopName(app.getShopName());
    dto.setDescription(app.getDescription());
    dto.setStatus(app.getStatus());
    dto.setRejectionReason(app.getRejectionReason());
    dto.setDecidedAt(app.getDecidedAt());
    dto.setDecidedByUserId(app.getDecidedByUserId());
    dto.setCreatedAt(app.getCreatedAt());
    return dto;
  }
}

