package com.socialwallet.moderation.service;

import com.socialwallet.moderation.model.ModerationActionLog;
import com.socialwallet.moderation.model.ModerationActionLogExecutionStatus;
import com.socialwallet.moderation.model.ModerationActionType;
import com.socialwallet.moderation.model.ModerationTargetType;
import com.socialwallet.moderation.repository.ModerationActionLogRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ModerationActionLogService {
  private final ModerationActionLogRepository moderationActionLogRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ModerationActionLog record(UUID reportId,
                                    UUID moderatorUserId,
                                    ModerationTargetType targetType,
                                    String targetId,
                                    ModerationActionType actionType,
                                    ModerationActionLogExecutionStatus executionStatus,
                                    String details) {
    ModerationActionLog row = new ModerationActionLog();
    row.setReportId(reportId);
    row.setModeratorUserId(moderatorUserId);
    row.setTargetType(targetType);
    row.setTargetId(targetId);
    row.setActionType(actionType);
    row.setExecutionStatus(executionStatus);
    row.setDetails(trimToNull(details));
    return moderationActionLogRepository.save(row);
  }

  @Transactional(readOnly = true)
  public List<ModerationActionLog> listForReport(UUID reportId) {
    return moderationActionLogRepository.findTop200ByReportIdOrderByCreatedAtDesc(reportId);
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed;
  }
}
