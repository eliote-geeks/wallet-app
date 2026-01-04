package com.socialwallet.store.repository;

import com.socialwallet.store.model.StoreCustomerMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCustomerMappingRepository extends JpaRepository<StoreCustomerMapping, UUID> {
  Optional<StoreCustomerMapping> findByUserId(UUID userId);
}
