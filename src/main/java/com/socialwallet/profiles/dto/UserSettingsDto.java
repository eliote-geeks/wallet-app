package com.socialwallet.profiles.dto;

import com.socialwallet.profiles.model.PrivacyLevel;
import lombok.Data;

/**
 * DTO for reading and updating privacy settings.
 */
@Data
public class UserSettingsDto {
    private PrivacyLevel profilePhoto;
    private PrivacyLevel about;
    private PrivacyLevel lastSeenAndOnline;
    private boolean readReceipts;
    private PrivacyLevel defaultStoryVisibility;
}