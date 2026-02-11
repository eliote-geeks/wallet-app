package com.socialwallet.admin.repository;

import com.socialwallet.admin.model.RoleAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAuditLogRepository extends JpaRepository<RoleAuditLog, UUID> {
}
