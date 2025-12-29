package com.socialwallet.stories.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class StoryPreviewDto {
    private UUID authorId;
    private String authorName;
    private String authorPhotoUrl;
    private List<StoryItemDto> stories;
    private int unviewedCount;
    private LocalDateTime lastStoryAt;
}