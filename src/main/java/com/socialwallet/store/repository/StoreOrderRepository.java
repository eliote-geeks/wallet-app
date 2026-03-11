package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreOrder;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, UUID> {
  Optional<StoreOrder> findByMedusaOrderId(String medusaOrderId);

  Page<StoreOrder> findByBuyerUserId(UUID buyerUserId, Pageable pageable);
}
