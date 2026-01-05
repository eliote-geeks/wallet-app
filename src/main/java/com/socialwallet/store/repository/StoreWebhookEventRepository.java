package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreWebhookEvent;
import com.socialwallet.store.model.StoreWebhookEventStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreWebhookEventRepository extends JpaRepository<StoreWebhookEvent, UUID> {
  Page<StoreWebhookEvent> findByStatus(StoreWebhookEventStatus status, Pageable pageable);
}
