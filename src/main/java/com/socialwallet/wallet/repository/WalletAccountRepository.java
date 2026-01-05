package com.socialwallet.wallet.repository;

import jakarta.persistence.LockModeType;
import com.socialwallet.wallet.model.WalletAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WalletAccount w where w.userId = :userId")
  Optional<WalletAccount> findForUpdate(@Param("userId") UUID userId);
}
