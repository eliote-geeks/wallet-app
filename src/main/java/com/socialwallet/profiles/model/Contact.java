package com.socialwallet.profiles.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "contact_id"}))
@Data
public class Contact {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    private Instant createdAt = Instant.now();
}