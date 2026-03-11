package com.socialwallet.moderation.service;

import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.moderation.ModerationException;
import com.socialwallet.moderation.dto.ModerationReportCreateRequest;
import com.socialwallet.moderation.dto.ModerationReportDecisionRequest;
import com.socialwallet.moderation.model.ModerationActionLog;
import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationReport;
import com.socialwallet.moderation.model.ModerationReportStatus;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.moderation.repository.ModerationReportRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModerationService {
  private final ModerationReportRepository moderationReportRepository;
  private final UserAccountRepository userAccountRepository;
  private final ModerationActionExecutor moderationActionExecutor;
  private final ModerationActionLogService moderationActionLogService;

  @Transactional
  public ModerationReport createReport(UUID reporterUserId, ModerationReportCreateRequest request) {
    ensureUserExists(reporterUserId);
    ModerationReport report = new ModerationReport();
    report.setReporterUserId(reporterUserId);
    report.setTargetType(request.getTargetType());
    report.setTargetId(request.getTargetId().trim());
    report.setReasonCode(request.getReasonCode().trim().toUpperCase());
    report.setDescription(trimToNull(request.getDescription()));
    report.setStatus(ModerationReportStatus.OPEN);
    report.setActionType(ModerationActionType.NONE);
    return moderationReportRepository.save(report);
  }

  @Transactional(readOnly = true)
  public List<ModerationReport> listMyReports(UUID reporterUserId) {
    ensureUserExists(reporterUserId);
    return moderationReportRepository.findTop100ByReporterUserIdOrderByCreatedAtDesc(reporterUserId);
  }

  @Transactional(readOnly = true)
  public Page<ModerationReport> listQueue(ModerationReportStatus status,
                                          ModerationTargetType targetType,
                                          UUID reporterUserId,
                                          String targetId,
                                          Pageable pageable) {
    Specification<ModerationReport> spec = Specification.where(null);
    if (status != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
    }
    if (targetType != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("targetType"), targetType));
    }
    if (reporterUserId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("reporterUserId"), reporterUserId));
    }
    String normalizedTargetId = trimToNull(targetId);
    if (normalizedTargetId != null) {
      spec = spec.and((root, query, cb) -> cb.equal(root.get("targetId"), normalizedTargetId));
    }

    Pageable effectivePageable = pageable;
    if (effectivePageable == null) {
      effectivePageable = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "createdAt"));
    } else if (effectivePageable.getSort().isUnsorted()) {
      effectivePageable = PageRequest.of(
        effectivePageable.getPageNumber(),
        effectivePageable.getPageSize(),
        Sort.by(Sort.Direction.DESC, "createdAt")
      );
    }
    return moderationReportRepository.findAll(spec, effectivePageable);
  }

  @Transactional
  public ModerationReport updateStatus(UUID moderatorUserId, UUID reportId, ModerationReportDecisionRequest request) {
    ensureUserExists(moderatorUserId);
    ModerationReport report = moderationReportRepository.findById(reportId)
      .orElseThrow(() -> new ModerationException(HttpStatus.NOT_FOUND, "Moderation report not found"));
    validateTargetStatus(request.getStatus());
    ModerationActionType actionType = request.getActionType() == null ? ModerationActionType.NONE : request.getActionType();
    validateActionForStatus(request.getStatus(), actionType);

    ModerationActionExecutor.ExecutionResult executionResult = buildDefaultExecution(request.getStatus(), actionType);
    if (request.getStatus() == ModerationReportStatus.RESOLVED) {
      executionResult = moderationActionExecutor.execute(report, actionType, moderatorUserId);
    }
    moderationActionLogService.record(
      report.getId(),
      moderatorUserId,
      report.getTargetType(),
      report.getTargetId(),
      actionType,
      executionResult.status(),
      executionResult.details()
    );
    if (executionResult.status() == ModerationActionLogExecutionStatus.FAILED) {
      throw new ModerationException(HttpStatus.BAD_GATEWAY, executionResult.details());
    }

    report.setStatus(request.getStatus());
    report.setActionType(actionType);
    report.setAssignedModeratorUserId(moderatorUserId);
    report.setResolutionNote(trimToNull(request.getResolutionNote()));
    if (request.getStatus() == ModerationReportStatus.RESOLVED || request.getStatus() == ModerationReportStatus.REJECTED) {
      report.setResolvedAt(Instant.now());
    } else {
      report.setResolvedAt(null);
    }
    return moderationReportRepository.save(report);
  }

  @Transactional(readOnly = true)
  public List<ModerationActionLog> listActionLogs(UUID reportId) {
    if (reportId == null) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "reportId is required");
    }
    if (!moderationReportRepository.existsById(reportId)) {
      throw new ModerationException(HttpStatus.NOT_FOUND, "Moderation report not found");
    }
    return moderationActionLogService.listForReport(reportId);
  }

  private void validateTargetStatus(ModerationReportStatus status) {
    if (status == null) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "Status is required");
    }
    if (status == ModerationReportStatus.OPEN) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "Use OPEN only for creation");
    }
  }

  private void validateActionForStatus(ModerationReportStatus status, ModerationActionType actionType) {
    if (status != ModerationReportStatus.RESOLVED && actionType != ModerationActionType.NONE) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "Action type is allowed only when status is RESOLVED");
    }
  }

  private ModerationActionExecutor.ExecutionResult buildDefaultExecution(ModerationReportStatus status,
                                                                         ModerationActionType actionType) {
    if (actionType == ModerationActionType.NONE) {
      return ModerationActionExecutor.ExecutionResult.skipped("No automated action requested");
    }
    return ModerationActionExecutor.ExecutionResult.skipped("Action not executed for status " + status.name());
  }

  private void ensureUserExists(UUID userId) {
    if (userId == null || !userAccountRepository.existsById(userId)) {
      throw new ModerationException(HttpStatus.NOT_FOUND, "User not found");
    }
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
