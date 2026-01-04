package com.socialwallet.identity.repository;

import com.socialwallet.identity.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
  Optional<UserAccount> findByEmailIgnoreCase(String email);

  Optional<UserAccount> findByPhoneNumber(String phoneNumber);

  boolean existsByEmailIgnoreCase(String email);

  boolean existsByPhoneNumber(String phoneNumber);
}
