package com.socialwallet.moderation.web;

import com.socialwallet.moderation.dto.ModerationReportCreateRequest;
import com.socialwallet.moderation.dto.ModerationReportDecisionRequest;
import com.socialwallet.moderation.dto.ModerationReportResponse;
import com.socialwallet.moderation.model.ModerationReport;
import com.socialwallet.moderation.model.ModerationReportStatus;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.moderation.service.ModerationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import static com.socialwallet.config.RbacExpressions.MODERATOR_OR_ADMIN;
import static com.socialwallet.config.RbacExpressions.PLATFORM_USER;

@RestController
@RequestMapping("/api/moderation/reports")
@RequiredArgsConstructor
@PreAuthorize(PLATFORM_USER)
public class ModerationController {
  private final ModerationService moderationService;

  @PostMapping
  public ResponseEntity<ModerationReportResponse> create(@AuthenticationPrincipal Jwt jwt,
                                                         @Valid @RequestBody ModerationReportCreateRequest request) {
    UUID reporterUserId = UUID.fromString(jwt.getSubject());
    ModerationReport saved = moderationService.createReport(reporterUserId, request);
    return ResponseEntity.ok(toResponse(saved));
  }

  @GetMapping("/my")
  public ResponseEntity<List<ModerationReportResponse>> myReports(@AuthenticationPrincipal Jwt jwt) {
    UUID reporterUserId = UUID.fromString(jwt.getSubject());
    List<ModerationReportResponse> rows = moderationService.listMyReports(reporterUserId)
      .stream()
      .map(this::toResponse)
      .toList();
    return ResponseEntity.ok(rows);
  }

  @GetMapping("/queue")
  @PreAuthorize(MODERATOR_OR_ADMIN)
  public ResponseEntity<Page<ModerationReportResponse>> queue(
    @RequestParam(name = "status", required = false) ModerationReportStatus status,
    @RequestParam(name = "targetType", required = false) ModerationTargetType targetType,
    @RequestParam(name = "reporterUserId", required = false) UUID reporterUserId,
    @RequestParam(name = "targetId", required = false) String targetId,
    @PageableDefault(size = 50, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC)
    Pageable pageable) {
    Page<ModerationReportResponse> page = moderationService
      .listQueue(status, targetType, reporterUserId, targetId, pageable)
      .map(this::toResponse);
    return ResponseEntity.ok(page);
  }

  @PatchMapping("/{reportId}/status")
  @PreAuthorize(MODERATOR_OR_ADMIN)
  public ResponseEntity<ModerationReportResponse> updateStatus(@AuthenticationPrincipal Jwt jwt,
                                                               @PathVariable UUID reportId,
                                                               @Valid @RequestBody ModerationReportDecisionRequest request) {
    UUID moderatorUserId = UUID.fromString(jwt.getSubject());
    ModerationReport updated = moderationService.updateStatus(moderatorUserId, reportId, request);
    return ResponseEntity.ok(toResponse(updated));
  }

  private ModerationReportResponse toResponse(ModerationReport row) {
    ModerationReportResponse response = new ModerationReportResponse();
    response.setId(row.getId());
    response.setReporterUserId(row.getReporterUserId());
    response.setTargetType(row.getTargetType());
    response.setTargetId(row.getTargetId());
    response.setReasonCode(row.getReasonCode());
    response.setDescription(row.getDescription());
    response.setStatus(row.getStatus());
    response.setActionType(row.getActionType());
    response.setAssignedModeratorUserId(row.getAssignedModeratorUserId());
    response.setResolutionNote(row.getResolutionNote());
    response.setResolvedAt(row.getResolvedAt());
    response.setCreatedAt(row.getCreatedAt());
    response.setUpdatedAt(row.getUpdatedAt());
    return response;
  }
}
