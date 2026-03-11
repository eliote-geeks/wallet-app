package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreSeller;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreSellerRepository extends JpaRepository<StoreSeller, UUID> {
  Optional<StoreSeller> findByUserId(UUID userId);
}

