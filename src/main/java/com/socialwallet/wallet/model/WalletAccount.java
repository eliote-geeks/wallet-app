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
@Table(name = "wallet_accounts")
@Data
public class WalletAccount {
  @Id
  @Column(name = "user_id")
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
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
