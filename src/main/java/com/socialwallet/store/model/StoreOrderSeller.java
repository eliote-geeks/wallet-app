package com.socialwallet.store.model;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
  name = "store_order_sellers",
  uniqueConstraints = {
    @UniqueConstraint(name = "uq_store_order_sellers_order_seller", columnNames = {"order_id", "seller_user_id"})
  }
)
@Data
public class StoreOrderSeller {
  @Id
  private UUID id;

  @Column(name = "order_id", nullable = false)
  private UUID orderId;

  @Column(name = "seller_user_id", nullable = false)
  private UUID sellerUserId;

  @Column(name = "currency_code", nullable = false, length = 10)
  private String currencyCode;

  @Column(name = "gross_amount", nullable = false)
  private Long grossAmount;

  @Column(name = "platform_fee_amount", nullable = false)
  private Long platformFeeAmount = 0L;

  @Column(name = "net_amount", nullable = false)
  private Long netAmount;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private StoreOrderSellerStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "items", columnDefinition = "jsonb")
  private JsonNode items;

  @Column(name = "settled_wallet_tx_id")
  private UUID settledWalletTxId;

  @Column(name = "settled_at")
  private Instant settledAt;

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
