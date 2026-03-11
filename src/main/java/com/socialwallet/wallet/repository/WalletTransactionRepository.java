package com.socialwallet.wallet.repository;

import com.socialwallet.wallet.model.WalletTransaction;
import com.socialwallet.wallet.model.WalletTransactionType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
  List<WalletTransaction> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  Optional<WalletTransaction> findByUserIdAndTypeAndReferenceTypeAndReferenceId(
    UUID userId,
    WalletTransactionType type,
    String referenceType,
    String referenceId
  );
}
