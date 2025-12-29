package com.socialwallet.stories.dto;

import com.socialwallet.stories.model.MediaType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StoryItemDto {
    private UUID storyId;
    private MediaType mediaType;
    private String mediaUrl;
    private String caption;
    private String textContent;
    private String backgroundColor;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean viewedByMe;
}