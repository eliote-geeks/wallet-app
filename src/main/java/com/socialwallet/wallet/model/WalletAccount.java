package com.socialwallet.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(
  name = "wallet_accounts",
  uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(name = "uq_wallet_accounts_user_currency", columnNames = {"user_id", "currency_code"})
  }
)
@Data
public class WalletAccount {
  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "currency_code", nullable = false, length = 10)
  private String currencyCode;

  @Column(name = "available_amount", nullable = false)
  private Long availableAmount = 0L;

  @Column(name = "reserved_amount", nullable = false)
  private Long reservedAmount = 0L;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @PrePersist
  protected void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
