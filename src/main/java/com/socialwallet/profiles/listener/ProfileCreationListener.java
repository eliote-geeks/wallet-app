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

@Component
@RequiredArgsConstructor
@Slf4j
public class ProfileCreationListener {

    private final ProfileRepository profileRepository;
    private final UserSettingsRepository settingsRepository;

    @EventListener
    @Transactional
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        if (event.getAuthentication().getPrincipal() instanceof Jwt jwt) {
            UUID userId = UUID.fromString(jwt.getSubject());

            log.info("Successful login for userId: {}", userId);

            if (profileRepository.findByUserId(userId).isEmpty()) {
                Profile profile = new Profile();
                profile.setUserId(userId);

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
                // avatarMediaId stays null — no avatar by default

                profileRepository.save(profile);
                log.info("Created new profile for userId: {}", userId);
            }

            if (settingsRepository.findByUserId(userId).isEmpty()) {
                UserSettings settings = new UserSettings();
                settings.setUserId(userId);
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