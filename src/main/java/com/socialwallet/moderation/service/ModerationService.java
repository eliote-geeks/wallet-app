package com.socialwallet.moderation.service;

import com.socialwallet.identity.repository.UserAccountRepository;
import com.socialwallet.moderation.ModerationException;
import com.socialwallet.moderation.dto.ModerationReportCreateRequest;
import com.socialwallet.moderation.dto.ModerationReportDecisionRequest;
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
    report.setStatus(request.getStatus());
    report.setActionType(request.getActionType() == null ? ModerationActionType.NONE : request.getActionType());
    report.setAssignedModeratorUserId(moderatorUserId);
    report.setResolutionNote(trimToNull(request.getResolutionNote()));
    if (request.getStatus() == ModerationReportStatus.RESOLVED || request.getStatus() == ModerationReportStatus.REJECTED) {
      report.setResolvedAt(Instant.now());
    } else {
      report.setResolvedAt(null);
    }
    return moderationReportRepository.save(report);
  }

  private void validateTargetStatus(ModerationReportStatus status) {
    if (status == null) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "Status is required");
    }
    if (status == ModerationReportStatus.OPEN) {
      throw new ModerationException(HttpStatus.BAD_REQUEST, "Use OPEN only for creation");
    }
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
