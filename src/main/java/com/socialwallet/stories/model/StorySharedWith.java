package com.socialwallet.stories.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "story_shared_with")
@Data
@IdClass(StorySharedWithId.class)
public class StorySharedWith {
    @Id
    @Column(name = "story_id")
    private UUID storyId;

    @Id
    @Column(name = "shared_user_id")
    private UUID sharedUserId;
}