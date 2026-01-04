package com.socialwallet.identity.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

@Entity
@Table(name = "user_accounts")
@Data
public class UserAccount {
  @Id
  private UUID id;

  @Column(name = "email", length = 320, unique = true)
  private String email;

  @Column(name = "phone_number", length = 32, unique = true)
  private String phoneNumber;

  @Column(name = "openim_user_id", unique = true)
  private Long openimUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private AccountStatus status = AccountStatus.PENDING_VERIFICATION;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "verified_at")
  private Instant verifiedAt;

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
