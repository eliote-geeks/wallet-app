package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.store.model.StoreWebhookEventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StoreWebhookEventRepository extends JpaRepository<StoreWebhookEvent, UUID> {
  Page<StoreWebhookEvent> findByStatus(StoreWebhookEventStatus status, Pageable pageable);

  @Query("""
    select e.id
    from StoreWebhookEvent e
    where e.provider = :provider
      and e.status = :status
      and e.attempts < :maxAttempts
      and (e.nextRetryAt is null or e.nextRetryAt <= :now)
    order by e.createdAt asc
    """)
  List<UUID> findDueIdsForRetry(@Param("provider") String provider,
                               @Param("status") StoreWebhookEventStatus status,
                               @Param("now") Instant now,
                               @Param("maxAttempts") int maxAttempts,
                               Pageable pageable);

  @Modifying
  @Transactional
  @Query("""
    update StoreWebhookEvent e
    set e.status = :toStatus,
        e.updatedAt = :now
    where e.id = :id
      and e.status = :fromStatus
    """)
  int transitionStatus(@Param("id") UUID id,
                       @Param("fromStatus") StoreWebhookEventStatus fromStatus,
                       @Param("toStatus") StoreWebhookEventStatus toStatus,
                       @Param("now") Instant now);
}
