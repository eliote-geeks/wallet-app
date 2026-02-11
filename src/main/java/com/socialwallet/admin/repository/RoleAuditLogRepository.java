package com.socialwallet.admin.repository;

import com.socialwallet.admin.model.RoleAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, UUID>,
  JpaSpecificationExecutor<RoleAuditLog> {
}
