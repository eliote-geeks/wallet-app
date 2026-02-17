package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreSellerApplication;
import com.socialwallet.store.model.StoreSellerApplicationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSellerApplicationRepository extends JpaRepository<StoreSellerApplication, UUID> {
  Optional<StoreSellerApplication> findFirstByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, StoreSellerApplicationStatus status);

  Optional<StoreSellerApplication> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

  Page<StoreSellerApplication> findByStatus(StoreSellerApplicationStatus status, Pageable pageable);
}
