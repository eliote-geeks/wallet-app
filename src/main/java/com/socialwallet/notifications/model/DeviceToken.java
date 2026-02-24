package com.socialwallet.notifications.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_tokens", indexes = {
    @Index(name = "idx_device_tokens_user", columnList = "user_id, active")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private Platform platform;

    @Column(name = "token", nullable = false, columnDefinition = "TEXT")
    private String token;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) {
            registeredAt = Instant.now();
        }
        if (lastUsedAt == null) {
            lastUsedAt = Instant.now();
        }
    }
}
