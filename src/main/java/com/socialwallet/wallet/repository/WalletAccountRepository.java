package com.socialwallet.wallet.repository;

import jakarta.persistence.LockModeType;
import com.socialwallet.wallet.model.WalletAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletAccountRepository extends JpaRepository<WalletAccount, UUID> {
  Optional<WalletAccount> findByUserIdAndCurrencyCodeIgnoreCase(UUID userId, String currencyCode);

  List<WalletAccount> findByUserId(UUID userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WalletAccount w where w.userId = :userId and lower(w.currencyCode) = lower(:currencyCode)")
  Optional<WalletAccount> findForUpdate(@Param("userId") UUID userId, @Param("currencyCode") String currencyCode);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select w from WalletAccount w where w.id = :id")
  Optional<WalletAccount> findForUpdateById(@Param("id") UUID id);
}
