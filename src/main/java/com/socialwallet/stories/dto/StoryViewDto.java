package com.socialwallet.stories.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class StoryViewDto {
    private UUID viewerId;
    private String viewerName;
    private String viewerPhotoUrl;
    private LocalDateTime viewedAt;
}