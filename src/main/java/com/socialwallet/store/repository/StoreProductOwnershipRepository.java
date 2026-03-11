package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreProductOwnership;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreProductOwnershipRepository extends JpaRepository<StoreProductOwnership, String> {
  Optional<StoreProductOwnership> findByProductId(String productId);
}
