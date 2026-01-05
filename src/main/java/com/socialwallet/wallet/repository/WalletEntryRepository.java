package com.socialwallet.wallet.repository;

import com.socialwallet.wallet.model.WalletEntry;
import com.socialwallet.wallet.model.WalletEntryBalanceType;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {
  @Query("""
    select coalesce(sum(
      case when e.direction = com.socialwallet.wallet.model.WalletEntryDirection.CREDIT
        then e.amount
        else -e.amount
      end
    ), 0)
    from WalletEntry e
    where e.accountId = :accountId and e.balanceType = :balanceType
    """)
  Long sumForBalanceType(@Param("accountId") UUID accountId,
                         @Param("balanceType") WalletEntryBalanceType balanceType);
}
