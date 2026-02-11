package com.socialwallet.calls.model;

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
@Table(name = "call_history")
@Data
public class CallHistory {
  @Id
  private UUID id;

  @Column(name = "call_id", nullable = false, unique = true)
  private UUID callId;

  @Column(name = "room_name", nullable = false, length = 150)
  private String roomName;

  @Column(name = "audio_only", nullable = false)
  private boolean audioOnly;

  @Column(name = "initiator_user_id", nullable = false)
  private UUID initiatorUserId;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private CallStatus status;

  @Column(name = "initiated_at", nullable = false)
  private Instant initiatedAt;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Column(name = "ended_at")
  private Instant endedAt;

  @Column(name = "duration_seconds")
  private Long durationSeconds;

  @Column(name = "last_signal_from_user_id")
  private UUID lastSignalFromUserId;

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
