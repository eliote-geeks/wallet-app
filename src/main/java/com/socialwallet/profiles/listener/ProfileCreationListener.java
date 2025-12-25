package com.socialwallet.profiles.listener;

import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Listener that automatically creates a user profile and default privacy settings
 * on the first successful login via Keycloak.
 * 
 * This ensures that every authenticated user has a profile immediately after registration/login,
 * allowing the app to function correctly from the first connection (WhatsApp-like behavior).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileCreationListener {

    private final ProfileRepository profileRepository;
    private final UserSettingsRepository settingsRepository;

    /**
     * Triggered on every successful authentication (Keycloak login).
     * Creates profile and settings if they do not already exist.
     */
    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof Jwt jwt) {
            UUID userId = UUID.fromString(jwt.getSubject());

            log.info("Successful login for userId: {}", userId);

            // Create profile if not exists
            if (profileRepository.findByUserId(userId).isEmpty()) {
                Profile profile = new Profile();
                profile.setUserId(userId);

                // Try to populate name from Keycloak claims (common claims)
                String name = jwt.getClaimAsString("name");
                if (name == null || name.isBlank()) {
                    name = jwt.getClaimAsString("preferred_username");
                }
                if (name == null || name.isBlank()) {
                    name = jwt.getClaimAsString("given_name");
                    if (name != null && !name.isBlank()) {
                        String familyName = jwt.getClaimAsString("family_name");
                        if (familyName != null && !familyName.isBlank()) {
                            name = name + " " + familyName;
                        }
                    }
                }
                profile.setName(name != null ? name.trim() : "");

                profile.setAbout("");
                profile.setPhotoUrl(""); // or default avatar URL if you have one

                profileRepository.save(profile);
                log.info("Created new profile for userId: {}", userId);
            }

            // Create default privacy settings if not exists
            if (settingsRepository.findByUserId(userId).isEmpty()) {
                UserSettings settings = new UserSettings();
                settings.setUserId(userId);
                // Defaults as defined in your requirements
                settings.setProfilePhoto(PrivacyLevel.EVERYONE);
                settings.setAbout(PrivacyLevel.EVERYONE);
                settings.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
                settings.setReadReceipts(true);
                settings.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);

                settingsRepository.save(settings);
                log.info("Created default privacy settings for userId: {}", userId);
            }
        }
    }
}