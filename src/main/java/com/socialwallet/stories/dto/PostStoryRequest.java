package com.socialwallet.stories.dto;

import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.stories.model.MediaType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class PostStoryRequest {

    @NotNull(message = "Media type is required")
    private MediaType mediaType;

    private UUID mediaId;           // Required for IMAGE/VIDEO (reference to Media module)
    private String caption;         // Optional for IMAGE/VIDEO
    private String textContent;     // Required for TEXT
    private String backgroundColor; // Required for TEXT
    private PrivacyLevel visibility; // Optional, defaults to UserSettings
    private List<UUID> hiddenFrom;  // "My contacts except..."
    private List<UUID> sharedWith;  // "Share only with..."
}