package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, UUID> {
  Optional<StoreOrder> findByMedusaOrderId(String medusaOrderId);
}
