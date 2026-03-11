package com.socialwallet.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(
  name = "wallet_holds",
  uniqueConstraints = {
    @UniqueConstraint(name = "uq_wallet_holds_user_cart", columnNames = {"user_id", "cart_id"})
  }
)
@Data
public class WalletHold {
  @Id
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "cart_id", nullable = false, length = 64)
  private String cartId;

  @Column(name = "currency_code", nullable = false, length = 10)
  private String currencyCode;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private WalletHoldStatus status;

  @Column(name = "failure_reason", length = 500)
  private String failureReason;

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
