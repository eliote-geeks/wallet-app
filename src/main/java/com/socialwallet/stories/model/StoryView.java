package com.socialwallet.stories.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "story_views")
@Data
@IdClass(StoryViewId.class)
public class StoryView {

    @Id
    @Column(name = "story_id", nullable = false)
    private UUID storyId;

    @Id
    @Column(name = "viewer_id", nullable = false)
    private UUID viewerId;

    @Column(name = "viewed_at", nullable = false)
    private LocalDateTime viewedAt;

    @PrePersist
    protected void onCreate() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }
}