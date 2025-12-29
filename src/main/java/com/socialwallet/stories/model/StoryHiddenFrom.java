package com.socialwallet.stories.model;

import jakarta.persistence.*;
import lombok.Data;
import java.util.UUID;

@Entity
@Table(name = "story_hidden_from")
@Data
@IdClass(StoryHiddenFromId.class)
public class StoryHiddenFrom {
    @Id
    @Column(name = "story_id")
    private UUID storyId;

    @Id
    @Column(name = "hidden_user_id")
    private UUID hiddenUserId;
}