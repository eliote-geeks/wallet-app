package com.socialwallet.profiles.dto;

import lombok.Data;
import java.util.UUID;

/**
 * Full profile DTO returned to the owner.
 */
@Data
public class MyProfileDto {
    private UUID userId;
    private String name;
    private String about;
    private String photoUrl;
}