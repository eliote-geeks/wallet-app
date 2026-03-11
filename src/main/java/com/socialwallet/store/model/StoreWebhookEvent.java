package com.socialwallet.store.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "store_webhook_events")
@Data
public class StoreWebhookEvent {
  @Id
  private UUID id;

  @Column(name = "provider", nullable = false, length = 50)
  private String provider;

  @Column(name = "event_name", length = 255)
  private String eventName;

  @Column(name = "signature", length = 255)
  private String signature;

  @Column(name = "payload", columnDefinition = "TEXT")
  private String payload;

  @Column(name = "headers", columnDefinition = "TEXT")
  private String headers;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private StoreWebhookEventStatus status;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "last_error", columnDefinition = "TEXT")
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "processed_at")
  private Instant processedAt;

  @Column(name = "next_retry_at")
  private Instant nextRetryAt;

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
