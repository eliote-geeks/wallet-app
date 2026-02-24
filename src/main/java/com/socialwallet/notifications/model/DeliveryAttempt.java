package com.socialwallet.notifications.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts", indexes = {
    @Index(name = "idx_delivery_attempts_notification", columnList = "notification_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private NotificationChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }
}
