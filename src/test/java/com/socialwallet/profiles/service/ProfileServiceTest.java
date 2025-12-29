package com.socialwallet.profiles.service;

import com.socialwallet.profiles.dto.MyProfileDto;
import com.socialwallet.profiles.dto.ProfileDto;
import com.socialwallet.profiles.dto.UserSettingsDto;
import com.socialwallet.profiles.model.*;
import com.socialwallet.profiles.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService - Unit Tests")
class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private UserSettingsRepository settingsRepository;

    @InjectMocks private ProfileService profileService;

    private final UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID targetId = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID viewerId = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private Profile createTestProfile() {
        Profile p = new Profile();
        p.setUserId(targetId);
        p.setName("Test User");
        p.setAbout("Visible bio");
        p.setPhotoUrl("photo.jpg");
        return p;
    }

    private UserSettings createTestSettings() {
        UserSettings s = new UserSettings();
        s.setUserId(targetId);
        s.setProfilePhoto(PrivacyLevel.EVERYONE);
        s.setAbout(PrivacyLevel.EVERYONE);
        s.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
        s.setReadReceipts(true);
        s.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        return s;
    }

    @Nested
    @DisplayName("Profile Retrieval Tests")
    class ProfileRetrieval {

        @Test
        @DisplayName("getMyProfile returns complete profile")
        void getMyProfile_returnsCompleteProfile() {
            Profile profile = createTestProfile();
            profile.setUserId(userId);
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

            MyProfileDto result = profileService.getMyProfile(userId);

            assertEquals(userId, result.getUserId());
            assertEquals("Test User", result.getName());
            assertEquals("Visible bio", result.getAbout());
            assertEquals("photo.jpg", result.getPhotoUrl());
        }

        @Test
        @DisplayName("getMyProfile throws when profile not found")
        void getMyProfile_throwsWhenNotFound() {
            when(profileRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                profileService.getMyProfile(userId)
            );
        }

        @Test
        @DisplayName("getProfileForViewer shows all when EVERYONE privacy")
        void getProfileForViewer_showsAllWhenEveryone() {
            Profile profile = createTestProfile();
            UserSettings settings = createTestSettings();

            when(profileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));
            when(settingsRepository.findByUserId(targetId)).thenReturn(Optional.of(settings));

            ProfileDto result = profileService.getProfileForViewer(viewerId, targetId);

            assertEquals("Visible bio", result.getAbout());
            assertEquals("photo.jpg", result.getPhotoUrl());
        }

        @Test
        @DisplayName("getProfileForViewer shows all when mutual contact and MY_CONTACTS privacy")
        void getProfileForViewer_showsAllWhenMutualContact() {
            Profile profile = createTestProfile();
            UserSettings settings = createTestSettings();
            settings.setProfilePhoto(PrivacyLevel.MY_CONTACTS);
            settings.setAbout(PrivacyLevel.MY_CONTACTS);

            when(profileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));
            when(settingsRepository.findByUserId(targetId)).thenReturn(Optional.of(settings));
            
            // Mock BOTH directions for mutual contact
            when(contactRepository.findByUserIdAndContactId(viewerId, targetId))
                .thenReturn(Optional.of(new Contact()));
            when(contactRepository.findByUserIdAndContactId(targetId, viewerId))
                .thenReturn(Optional.of(new Contact()));

            ProfileDto result = profileService.getProfileForViewer(viewerId, targetId);

            assertEquals("Visible bio", result.getAbout());
            assertEquals("photo.jpg", result.getPhotoUrl());
        }

        @Test
        @DisplayName("getProfileForViewer hides data when MY_CONTACTS and not mutual contact")
        void getProfileForViewer_hidesDataWhenNotMutualContact() {
            Profile profile = createTestProfile();
            UserSettings settings = createTestSettings();
            settings.setProfilePhoto(PrivacyLevel.MY_CONTACTS);
            settings.setAbout(PrivacyLevel.MY_CONTACTS);

            when(profileRepository.findByUserId(targetId)).thenReturn(Optional.of(profile));
            when(settingsRepository.findByUserId(targetId)).thenReturn(Optional.of(settings));
            
            // Only ONE direction - not mutual
            when(contactRepository.findByUserIdAndContactId(viewerId, targetId))
                .thenReturn(Optional.of(new Contact()));
            when(contactRepository.findByUserIdAndContactId(targetId, viewerId))
                .thenReturn(Optional.empty());

            ProfileDto result = profileService.getProfileForViewer(viewerId, targetId);

            assertNull(result.getAbout());
            assertNull(result.getPhotoUrl());
        }
    }

    @Nested
    @DisplayName("Contact Management Tests")
    class ContactManagement {

        @Test
        @DisplayName("addContact creates single unidirectional entry")
        void addContact_createsUnidirectionalEntry() {
            when(contactRepository.findByUserIdAndContactId(userId, contactId))
                .thenReturn(Optional.empty());
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any()))
                .thenReturn(Optional.empty());

            profileService.addContact(userId, contactId);

            verify(contactRepository, times(1)).save(any(Contact.class));
        }

        @Test
        @DisplayName("addContact is idempotent")
        void addContact_isIdempotent() {
            when(contactRepository.findByUserIdAndContactId(userId, contactId))
                .thenReturn(Optional.of(new Contact()));

            profileService.addContact(userId, contactId);

            verify(contactRepository, never()).save(any());
        }

        @Test
        @DisplayName("addContact throws when adding self")
        void addContact_throwsWhenAddingSelf() {
            assertThrows(IllegalArgumentException.class, () ->
                profileService.addContact(userId, userId)
            );
        }

        @Test
        @DisplayName("addContact throws when user is blocked")
        void addContact_throwsWhenBlocked() {
            when(blockRepository.findByBlockerIdAndBlockedId(userId, contactId))
                .thenReturn(Optional.of(new Block()));

            assertThrows(IllegalArgumentException.class, () ->
                profileService.addContact(userId, contactId)
            );
        }

        @Test
        @DisplayName("removeContact removes single unidirectional entry")
        void removeContact_removesUnidirectionalEntry() {
            profileService.removeContact(userId, contactId);

            verify(contactRepository, times(1)).deleteByUserIdAndContactId(userId, contactId);
        }

        @Test
        @DisplayName("getMyContacts returns contact list")
        void getMyContacts_returnsContactList() {
            Contact c1 = new Contact();
            c1.setUserId(userId);
            c1.setContactId(contactId);

            when(contactRepository.findAllByUserId(userId)).thenReturn(List.of(c1));

            var result = profileService.getMyContacts(userId);

            assertEquals(1, result.size());
            assertEquals(contactId, result.get(0).getContactId());
        }
    }

    @Nested
    @DisplayName("Blocking Tests")
    class Blocking {

        @Test
        @DisplayName("blockUser creates block entry without removing contacts")
        void blockUser_createsBlockWithoutRemovingContacts() {
            when(blockRepository.findByBlockerIdAndBlockedId(userId, contactId))
                .thenReturn(Optional.empty());

            profileService.blockUser(userId, contactId);

            verify(blockRepository).save(any(Block.class));
            verify(contactRepository, never()).deleteByUserIdAndContactId(any(), any());
        }

        @Test
        @DisplayName("blockUser is idempotent")
        void blockUser_isIdempotent() {
            when(blockRepository.findByBlockerIdAndBlockedId(userId, contactId))
                .thenReturn(Optional.of(new Block()));

            profileService.blockUser(userId, contactId);

            verify(blockRepository, never()).save(any());
        }

        @Test
        @DisplayName("blockUser throws when blocking self")
        void blockUser_throwsWhenBlockingSelf() {
            assertThrows(IllegalArgumentException.class, () ->
                profileService.blockUser(userId, userId)
            );
        }

        @Test
        @DisplayName("unblockUser removes block")
        void unblockUser_removesBlock() {
            Block block = new Block();
            when(blockRepository.findByBlockerIdAndBlockedId(userId, contactId))
                .thenReturn(Optional.of(block));

            profileService.unblockUser(userId, contactId);

            verify(blockRepository).delete(block);
        }

        @Test
        @DisplayName("getMyBlockedUsers returns blocked list")
        void getMyBlockedUsers_returnsBlockedList() {
            Block block = new Block();
            block.setBlockerId(userId);
            block.setBlockedId(contactId);

            when(blockRepository.findAllByBlockerId(userId)).thenReturn(List.of(block));

            var result = profileService.getMyBlockedUsers(userId);

            assertEquals(1, result.size());
            assertEquals(contactId, result.get(0).getContactId());
        }
    }

    @Nested
    @DisplayName("Settings Tests")
    class Settings {

        @Test
        @DisplayName("getMySettings returns settings")
        void getMySettings_returnsSettings() {
            UserSettings settings = createTestSettings();
            settings.setUserId(userId);
            when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));

            UserSettingsDto result = profileService.getMySettings(userId);

            assertEquals(PrivacyLevel.EVERYONE, result.getProfilePhoto());
            assertEquals(PrivacyLevel.EVERYONE, result.getAbout());
            assertEquals(PrivacyLevel.MY_CONTACTS, result.getLastSeenAndOnline());
            assertTrue(result.isReadReceipts());
            assertEquals(PrivacyLevel.MY_CONTACTS, result.getDefaultStoryVisibility());
        }

        @Test
        @DisplayName("getMySettings creates default when not found")
        void getMySettings_createsDefaultWhenNotFound() {
            when(settingsRepository.findByUserId(userId)).thenReturn(Optional.empty());
            when(settingsRepository.save(any(UserSettings.class))).thenAnswer(i -> i.getArgument(0));

            UserSettingsDto result = profileService.getMySettings(userId);

            assertNotNull(result);
            verify(settingsRepository).save(any(UserSettings.class));
        }

        @Test
        @DisplayName("updateMySettings updates all fields")
        void updateMySettings_updatesAllFields() {
            UserSettings settings = createTestSettings();
            settings.setUserId(userId);
            when(settingsRepository.findByUserId(userId)).thenReturn(Optional.of(settings));

            UserSettingsDto updated = new UserSettingsDto();
            updated.setProfilePhoto(PrivacyLevel.MY_CONTACTS);
            updated.setAbout(PrivacyLevel.NOBODY);
            updated.setLastSeenAndOnline(PrivacyLevel.NOBODY);
            updated.setReadReceipts(false);
            updated.setDefaultStoryVisibility(PrivacyLevel.EVERYONE);

            profileService.updateMySettings(userId, updated);

            verify(settingsRepository).save(settings);
            assertEquals(PrivacyLevel.MY_CONTACTS, settings.getProfilePhoto());
            assertEquals(PrivacyLevel.NOBODY, settings.getAbout());
            assertEquals(PrivacyLevel.NOBODY, settings.getLastSeenAndOnline());
            assertFalse(settings.isReadReceipts());
            assertEquals(PrivacyLevel.EVERYONE, settings.getDefaultStoryVisibility());
        }
    }

    @Nested
    @DisplayName("Existence Check Tests")
    class ExistenceCheck {

        @Test
        @DisplayName("existsByUserId returns true when user exists")
        void existsByUserId_returnsTrueWhenExists() {
            when(profileRepository.existsByUserId(userId)).thenReturn(true);

            boolean result = profileService.existsByUserId(userId);

            assertTrue(result);
        }

        @Test
        @DisplayName("existsByUserId returns false when user does not exist")
        void existsByUserId_returnsFalseWhenNotExists() {
            when(profileRepository.existsByUserId(userId)).thenReturn(false);

            boolean result = profileService.existsByUserId(userId);

            assertFalse(result);
        }
    }
}