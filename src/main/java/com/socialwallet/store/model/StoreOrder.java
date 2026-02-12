package com.socialwallet.store.model;

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
@Table(name = "store_orders")
@Data
public class StoreOrder {
  @Id
  private UUID id;

  @Column(name = "medusa_order_id", nullable = false, length = 64, unique = true)
  private String medusaOrderId;

  @Column(name = "cart_id", length = 64)
  private String cartId;

  @Column(name = "buyer_user_id")
  private UUID buyerUserId;

  @Column(name = "currency_code", length = 10)
  private String currencyCode;

  @Column(name = "total_amount")
  private Long totalAmount;

  @Column(name = "order_status", length = 30)
  private String orderStatus;

  @Column(name = "payment_status", length = 30)
  private String paymentStatus;

  @Column(name = "fulfillment_status", length = 30)
  private String fulfillmentStatus;

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
