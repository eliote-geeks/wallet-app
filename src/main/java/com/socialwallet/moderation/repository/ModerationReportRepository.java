package com.socialwallet.moderation.repository;

import com.socialwallet.moderation.model.ModerationReport;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ModerationReportRepository extends JpaRepository<ModerationReport, UUID>,
  JpaSpecificationExecutor<ModerationReport> {

  List<ModerationReport> findTop100ByReporterUserIdOrderByCreatedAtDesc(UUID reporterUserId);
}
