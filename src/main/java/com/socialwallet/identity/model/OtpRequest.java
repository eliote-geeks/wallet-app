package com.socialwallet.identity.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "otp_requests")
@Data
public class OtpRequest {
  @Id
  @GeneratedValue
  private UUID id;

  @Column(name = "target", nullable = false, length = 320)
  private String target;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 20)
  private OtpChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "purpose", nullable = false, length = 30)
  private OtpPurpose purpose;

  @Column(name = "code_hash", nullable = false, length = 255)
  private String codeHash;

  @Column(name = "attempts", nullable = false)
  private int attempts;

  @Column(name = "max_attempts", nullable = false)
  private int maxAttempts;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
  }

  public boolean isExpired(Instant now) {
    return expiresAt != null && expiresAt.isBefore(now);
  }

  public boolean isConsumed() {
    return consumedAt != null;
  }
}
