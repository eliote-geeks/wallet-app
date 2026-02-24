package com.socialwallet.profiles.dto;

import lombok.Data;
import java.util.UUID;

/**
 * Full profile DTO returned to the owner.
 * avatarMediaId = stored reference (used for updates).
 * photoUrl = resolved display URL from Media module (read-only, for display).
 */
@Data
public class MyProfileDto {
    private UUID userId;
    private String name;
    private String about;
    private UUID avatarMediaId;
    private String photoUrl;  // Resolved by service, not stored
}