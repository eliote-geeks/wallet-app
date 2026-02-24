package com.socialwallet.stories.model;

import com.socialwallet.profiles.model.PrivacyLevel;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stories")
@Data
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    /**
     * Reference to the media asset managed by the Media module.
     * Required for IMAGE/VIDEO stories. Null for TEXT stories.
     */
    @Column(name = "media_id")
    private UUID mediaId;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "text_content", columnDefinition = "TEXT")
    private String textContent;

    @Column(name = "background_color", length = 10)
    private String backgroundColor;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private PrivacyLevel visibility;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24);
        }
    }
}