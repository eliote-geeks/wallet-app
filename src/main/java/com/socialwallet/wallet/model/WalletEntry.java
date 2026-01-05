package com.socialwallet.wallet.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "wallet_entries")
@Data
public class WalletEntry {
  @Id
  private UUID id;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "balance_type", nullable = false, length = 20)
  private WalletEntryBalanceType balanceType;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, length = 10)
  private WalletEntryDirection direction;

  @Column(name = "amount", nullable = false)
  private Long amount;

  @Column(name = "currency_code", nullable = false, length = 10)
  private String currencyCode;

  @Column(name = "description", length = 255)
  private String description;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    if (id == null) {
      id = UUID.randomUUID();
    }
    createdAt = Instant.now();
  }
}
