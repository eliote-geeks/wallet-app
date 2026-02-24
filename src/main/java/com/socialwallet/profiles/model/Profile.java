package com.socialwallet.profiles.model;

import com.socialwallet.profiles.listener.ProfileCreationListener;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Entity
@EntityListeners(ProfileCreationListener.class)
@Table(name = "profiles")
@Data
public class Profile {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", unique = true, nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String about;

    /**
     * Reference to the avatar media asset managed by the Media module.
     * Replaces the former photoUrl field.
     * Null means no avatar set (use default).
     */
    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}