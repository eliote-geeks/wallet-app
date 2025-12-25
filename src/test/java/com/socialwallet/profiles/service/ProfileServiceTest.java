package com.socialwallet.profiles.service;

import com.socialwallet.profiles.dto.ContactDto;
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

/**
 * Comprehensive unit tests for ProfileService.
 * 
 * All business logic is tested in isolation using Mockito.
 * Covers visibility rules, contact management, blocking, privacy settings, idempotence, and security rules.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileService - Unit Tests")
class ProfileServiceTest {

    @Mock private ProfileRepository profileRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private UserSettingsRepository settingsRepository;

    @InjectMocks private ProfileService profileService;

    private final UUID ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID contactId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID blockedId = UUID.fromString("00000000-0000-0000-0000-000000000003");

    // Helper method to create a test profile
    private Profile createProfile(UUID userId, String name, String about, String photoUrl) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setName(name);
        p.setAbout(about);
        p.setPhotoUrl(photoUrl);
        return p;
    }

    // Helper method to create test settings
    private UserSettings createSettings(UUID userId, PrivacyLevel photo, PrivacyLevel about) {
        UserSettings s = new UserSettings();
        s.setUserId(userId);
        s.setProfilePhoto(photo);
        s.setAbout(about);
        s.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
        s.setReadReceipts(true);
        s.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        return s;
    }

    @Nested
    @DisplayName("Profile Retrieval Tests")
    class ProfileRetrieval {

        @Test
        @DisplayName("getMyProfile returns the full profile for the owner")
        void getMyProfile_returnsFullProfile() {
            Profile profile = createProfile(ownerId, "Alice", "Online", "photo.jpg");
            when(profileRepository.findByUserId(ownerId)).thenReturn(Optional.of(profile));

            MyProfileDto dto = profileService.getMyProfile(ownerId);

            assertEquals(ownerId, dto.getUserId());
            assertEquals("Alice", dto.getName());
            assertEquals("Online", dto.getAbout());
            assertEquals("photo.jpg", dto.getPhotoUrl());
        }

        @Test
        @DisplayName("getMyProfile throws exception when profile not found")
        void getMyProfile_throwsWhenNotFound() {
            when(profileRepository.findByUserId(ownerId)).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> profileService.getMyProfile(ownerId));
        }

        @Test
        @DisplayName("getProfileForViewer hides fields when viewer is not a contact")
        void getProfileForViewer_hidesFieldsWhenNotContact() {
            Profile target = createProfile(contactId, "Bob", "Secret bio", "secret.jpg");
            UserSettings settings = createSettings(contactId, PrivacyLevel.MY_CONTACTS, PrivacyLevel.MY_CONTACTS);

            when(profileRepository.findByUserId(contactId)).thenReturn(Optional.of(target));
            when(settingsRepository.findByUserId(contactId)).thenReturn(Optional.of(settings));
            when(contactRepository.findByUserIdAndContactId(ownerId, contactId)).thenReturn(Optional.empty());

            ProfileDto dto = profileService.getProfileForViewer(ownerId, contactId);

            assertEquals(contactId, dto.getUserId());
            assertEquals("Bob", dto.getName());
            assertNull(dto.getAbout());
            assertNull(dto.getPhotoUrl());
        }

        @Test
        @DisplayName("getProfileForViewer shows all fields when viewer is a mutual contact")
        void getProfileForViewer_showsAllWhenContact() {
            Profile target = createProfile(contactId, "Bob", "Visible bio", "visible.jpg");
            UserSettings settings = createSettings(contactId, PrivacyLevel.MY_CONTACTS, PrivacyLevel.MY_CONTACTS);

            when(profileRepository.findByUserId(contactId)).thenReturn(Optional.of(target));
            when(settingsRepository.findByUserId(contactId)).thenReturn(Optional.of(settings));
            when(contactRepository.findByUserIdAndContactId(ownerId, contactId)).thenReturn(Optional.of(new Contact()));

            ProfileDto dto = profileService.getProfileForViewer(ownerId, contactId);

            assertEquals("Visible bio", dto.getAbout());
            assertEquals("visible.jpg", dto.getPhotoUrl());
        }

        @Test
        @DisplayName("getProfileForViewer shows all fields when visibility is EVERYONE")
        void getProfileForViewer_showsAllWhenEveryone() {
            Profile target = createProfile(contactId, "Bob", "Public bio", "public.jpg");
            UserSettings settings = createSettings(contactId, PrivacyLevel.EVERYONE, PrivacyLevel.EVERYONE);

            when(profileRepository.findByUserId(contactId)).thenReturn(Optional.of(target));
            when(settingsRepository.findByUserId(contactId)).thenReturn(Optional.of(settings));

            ProfileDto dto = profileService.getProfileForViewer(ownerId, contactId);

            assertEquals("Public bio", dto.getAbout());
            assertEquals("public.jpg", dto.getPhotoUrl());
        }
    }

    @Nested
    @DisplayName("Contact Management Tests")
    class ContactManagement {

        @Test
        @DisplayName("addContact creates mutual entries when not already contact")
        void addContact_createsMutualEntries() {
            when(contactRepository.findByUserIdAndContactId(any(), any())).thenReturn(Optional.empty());
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any())).thenReturn(Optional.empty());

            profileService.addContact(ownerId, contactId);

            verify(contactRepository, times(2)).save(any(Contact.class));
        }

        @Test
        @DisplayName("addContact is idempotent")
        void addContact_isIdempotent() {
            when(contactRepository.findByUserIdAndContactId(ownerId, contactId))
                    .thenReturn(Optional.of(new Contact()));
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any())).thenReturn(Optional.empty());

            profileService.addContact(ownerId, contactId);

            verify(contactRepository, never()).save(any());
        }

        @Test
        @DisplayName("addContact forbids self-add")
        void addContact_forbidsSelfAdd() {
            assertThrows(IllegalArgumentException.class, () ->
                    profileService.addContact(ownerId, ownerId));
        }

        @Test
        @DisplayName("addContact forbids adding a blocked user (either direction)")
        void addContact_forbidsIfBlocked() {
            when(blockRepository.findByBlockerIdAndBlockedId(any(UUID.class), any(UUID.class)))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(new Block())); // second call returns block

            assertThrows(IllegalArgumentException.class, () ->
                    profileService.addContact(ownerId, contactId));
        }

        @Test
        @DisplayName("removeContact removes both sides of the relationship")
        void removeContact_removesBothSides() {
            profileService.removeContact(ownerId, contactId);

            verify(contactRepository).deleteByUserIdAndContactId(ownerId, contactId);
            verify(contactRepository).deleteByUserIdAndContactId(contactId, ownerId);
        }

        @Test
        @DisplayName("getMyContacts returns list of contact IDs")
        void getMyContacts_returnsContactIds() {
            Contact c1 = new Contact();
            c1.setContactId(contactId);
            when(contactRepository.findAllByUserId(ownerId)).thenReturn(List.of(c1));

            List<ContactDto> dtos = profileService.getMyContacts(ownerId);

            assertEquals(1, dtos.size());
            assertEquals(contactId, dtos.get(0).getContactId());
        }
    }

    @Nested
    @DisplayName("Blocking Tests")
    class Blocking {

        @Test
        @DisplayName("blockUser creates block and removes mutual contacts")
        void blockUser_createsBlockAndRemovesContacts() {
            when(blockRepository.findByBlockerIdAndBlockedId(ownerId, blockedId))
                    .thenReturn(Optional.empty());

            profileService.blockUser(ownerId, blockedId);

            verify(blockRepository).save(any(Block.class));
            verify(contactRepository, times(2)).deleteByUserIdAndContactId(any(), any());
        }

        @Test
        @DisplayName("blockUser is idempotent")
        void blockUser_isIdempotent() {
            when(blockRepository.findByBlockerIdAndBlockedId(ownerId, blockedId))
                    .thenReturn(Optional.of(new Block()));

            profileService.blockUser(ownerId, blockedId);

            verify(blockRepository, never()).save(any());
        }

        @Test
        @DisplayName("blockUser forbids self-block")
        void blockUser_forbidsSelfBlock() {
            assertThrows(IllegalArgumentException.class, () ->
                    profileService.blockUser(ownerId, ownerId));
        }

        @Test
        @DisplayName("unblockUser removes the block")
        void unblockUser_removesBlock() {
            Block block = new Block();
            when(blockRepository.findByBlockerIdAndBlockedId(ownerId, blockedId))
                    .thenReturn(Optional.of(block));

            profileService.unblockUser(ownerId, blockedId);

            verify(blockRepository).delete(block);
        }

        @Test
        @DisplayName("getMyBlockedUsers returns list of blocked user IDs")
        void getMyBlockedUsers_returnsBlockedIds() {
            Block b1 = new Block();
            b1.setBlockedId(blockedId);
            when(blockRepository.findAllByBlockerId(ownerId)).thenReturn(List.of(b1));

            List<ContactDto> dtos = profileService.getMyBlockedUsers(ownerId);

            assertEquals(1, dtos.size());
            assertEquals(blockedId, dtos.get(0).getContactId());
        }
    }

    @Nested
    @DisplayName("Privacy Settings Tests")
    class PrivacySettings {

        @Test
        @DisplayName("getMySettings returns settings and creates defaults if missing")
        void getMySettings_createsDefaultsIfMissing() {
            when(settingsRepository.findByUserId(ownerId)).thenReturn(Optional.empty());
            when(settingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

            UserSettingsDto dto = profileService.getMySettings(ownerId);

            assertEquals(PrivacyLevel.EVERYONE, dto.getProfilePhoto());
            assertEquals(PrivacyLevel.EVERYONE, dto.getAbout());
            assertEquals(PrivacyLevel.MY_CONTACTS, dto.getLastSeenAndOnline());
            assertTrue(dto.isReadReceipts());
            assertEquals(PrivacyLevel.MY_CONTACTS, dto.getDefaultStoryVisibility());

            verify(settingsRepository).save(any(UserSettings.class));
        }

        @Test
        @DisplayName("updateMySettings applies only provided changes")
        void updateMySettings_appliesProvidedChanges() {
            UserSettings existing = createSettings(ownerId, PrivacyLevel.EVERYONE, PrivacyLevel.EVERYONE);
            when(settingsRepository.findByUserId(ownerId)).thenReturn(Optional.of(existing));

            UserSettingsDto update = new UserSettingsDto();
            update.setProfilePhoto(PrivacyLevel.NOBODY);
            update.setReadReceipts(false);
            update.setLastSeenAndOnline(PrivacyLevel.NOBODY);

            profileService.updateMySettings(ownerId, update);

            assertEquals(PrivacyLevel.NOBODY, existing.getProfilePhoto());
            assertFalse(existing.isReadReceipts());
            assertEquals(PrivacyLevel.NOBODY, existing.getLastSeenAndOnline());
            // Unchanged fields
            assertEquals(PrivacyLevel.EVERYONE, existing.getAbout());
            assertEquals(PrivacyLevel.MY_CONTACTS, existing.getDefaultStoryVisibility());

            verify(settingsRepository).save(existing);
        }
    }
}