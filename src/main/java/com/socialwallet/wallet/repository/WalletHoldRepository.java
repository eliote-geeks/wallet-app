package com.socialwallet.wallet.repository;

import com.socialwallet.wallet.model.WalletHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletHoldRepository extends JpaRepository<WalletHold, UUID> {
  Optional<WalletHold> findByUserIdAndCartId(UUID userId, String cartId);
}
