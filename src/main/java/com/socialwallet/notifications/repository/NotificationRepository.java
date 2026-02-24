package com.socialwallet.notifications.repository;

import com.socialwallet.notifications.model.Notification;
import com.socialwallet.notifications.model.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Query("UPDATE Notification n SET n.status = 'READ', n.readAt = :now WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsRead(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);

    long countByUserIdAndStatus(UUID userId, NotificationStatus status);
}
