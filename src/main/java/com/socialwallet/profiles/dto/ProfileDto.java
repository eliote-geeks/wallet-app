package com.socialwallet.profiles.dto;

import lombok.Data;
import java.util.UUID;

/**
 * Public profile DTO with visibility rules applied.
 * Sensitive fields may be null if hidden.
 */
@Data
public class ProfileDto {
    private UUID userId;
    private String name;
    private String about;     // null if hidden
    private String photoUrl;  // null if hidden
}