package com.socialwallet.profiles.listener;

import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileCreationListener - Unit Tests")
class ProfileCreationListenerTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private UserSettingsRepository settingsRepository;
    @Mock private Authentication authentication;
    @Mock private Jwt jwt;

    @InjectMocks private ProfileCreationListener listener;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    @DisplayName("Creates profile and settings on first login")
    void createsProfileAndSettingsOnFirstLogin() {
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(jwt.getClaimAsString("name")).thenReturn("John Doe");

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.empty());

        when(profileRepository.save(any(Profile.class))).thenAnswer(i -> i.getArgument(0));
        when(settingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

        listener.onAuthenticationSuccess(new AuthenticationSuccessEvent(authentication));

        // Verify profile creation — avatarMediaId is null by default
        verify(profileRepository).save(argThat(profile ->
                profile.getUserId().equals(userId) &&
                profile.getName().equals("John Doe") &&
                profile.getAbout().isEmpty() &&
                profile.getAvatarMediaId() == null
        ));

        // Verify settings creation with defaults
        verify(settingsRepository).save(argThat(settings ->
                settings.getUserId().equals(userId) &&
                settings.getProfilePhoto() == PrivacyLevel.EVERYONE &&
                settings.getAbout() == PrivacyLevel.EVERYONE &&
                settings.getLastSeenAndOnline() == PrivacyLevel.MY_CONTACTS &&
                settings.isReadReceipts() &&
                settings.getDefaultStoryVisibility() == PrivacyLevel.MY_CONTACTS
        ));
    }

    @Test
    @DisplayName("Does nothing if profile and settings already exist")
    void doesNothingIfAlreadyExists() {
        when(authentication.getPrincipal()).thenReturn(jwt);
        when(jwt.getSubject()).thenReturn(userId.toString());

        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(new Profile()));
        when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(new UserSettings()));

        listener.onAuthenticationSuccess(new AuthenticationSuccessEvent(authentication));

        verify(profileRepository, never()).save(any());
        verify(settingsRepository, never()).save(any());
    }
}