package com.socialwallet.wallet.repository;

import com.socialwallet.wallet.model.WalletEntry;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, UUID> {
}
