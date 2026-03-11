package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreOrderSeller;
import com.socialwallet.store.model.StoreOrderSellerStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreOrderSellerRepository extends JpaRepository<StoreOrderSeller, UUID> {
  Optional<StoreOrderSeller> findByOrderIdAndSellerUserId(UUID orderId, UUID sellerUserId);

  List<StoreOrderSeller> findByOrderId(UUID orderId);

  Page<StoreOrderSeller> findBySellerUserId(UUID sellerUserId, Pageable pageable);

  Page<StoreOrderSeller> findBySellerUserIdAndStatus(UUID sellerUserId, StoreOrderSellerStatus status, Pageable pageable);
}
