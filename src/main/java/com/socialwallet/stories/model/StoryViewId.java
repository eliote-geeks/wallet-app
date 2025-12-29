package com.socialwallet.stories.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoryViewId implements Serializable {
    private UUID storyId;
    private UUID viewerId;
}