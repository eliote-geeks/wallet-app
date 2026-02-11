package com.socialwallet.moderation.repository;

import com.socialwallet.moderation.model.ModerationActionLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationActionLogRepository extends JpaRepository<ModerationActionLog, UUID> {
  List<ModerationActionLog> findTop200ByReportIdOrderByCreatedAtDesc(UUID reportId);
}
