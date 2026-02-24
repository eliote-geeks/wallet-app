package com.socialwallet.profiles.service;

import com.socialwallet.profiles.dto.*;
import com.socialwallet.profiles.model.*;
import com.socialwallet.profiles.repository.*;
import com.socialwallet.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for Profiles & Contacts module.
 * Contains all business logic: visibility, mutual contacts, blocking.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final ProfileRepository profileRepository;
    private final ContactRepository contactRepository;
    private final BlockRepository blockRepository;
    private final UserSettingsRepository settingsRepository;
    private final MediaService mediaService;

    @Transactional(readOnly = true)
    public MyProfileDto getMyProfile(UUID userId) {
        Profile profile = getProfileEntity(userId);
        MyProfileDto dto = new MyProfileDto();
        dto.setUserId(profile.getUserId());
        dto.setName(profile.getName());
        dto.setAbout(profile.getAbout());
        dto.setAvatarMediaId(profile.getAvatarMediaId());
        dto.setPhotoUrl(resolveAvatarUrl(profile.getAvatarMediaId()));
        return dto;
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfileForViewer(UUID viewerId, UUID targetId) {
        Profile profile = getProfileEntity(targetId);
        UserSettings settings = getSettings(targetId);
        boolean isMutualContact = isMutualContact(viewerId, targetId);

        ProfileDto dto = new ProfileDto();
        dto.setUserId(profile.getUserId());
        dto.setName(profile.getName());

        // Photo visible si EVERYONE OU (MY_CONTACTS et contact mutuel)
        if (settings.getProfilePhoto() == PrivacyLevel.EVERYONE ||
            (settings.getProfilePhoto() == PrivacyLevel.MY_CONTACTS && isMutualContact)) {
            dto.setPhotoUrl(resolveAvatarUrl(profile.getAvatarMediaId()));
        }

        // About visible si EVERYONE OU (MY_CONTACTS et contact mutuel)
        if (settings.getAbout() == PrivacyLevel.EVERYONE ||
            (settings.getAbout() == PrivacyLevel.MY_CONTACTS && isMutualContact)) {
            dto.setAbout(profile.getAbout());
        }

        return dto;
    }

    @Transactional
    public void updateMyProfile(UUID userId, MyProfileDto updated) {
        Profile profile = getProfileEntity(userId);
        if (updated.getName() != null && !updated.getName().isBlank()) {
            profile.setName(updated.getName().trim());
        }
        if (updated.getAbout() != null) {
            profile.setAbout(updated.getAbout().trim());
        }
        if (updated.getAvatarMediaId() != null) {
            if (!mediaService.exists(updated.getAvatarMediaId())) {
                throw new IllegalArgumentException("Avatar media not found");
            }
            profile.setAvatarMediaId(updated.getAvatarMediaId());
        }
        profileRepository.save(profile);
    }

    @Transactional
    public void addContact(UUID userId, UUID contactId) {
        if (userId.equals(contactId)) {
            throw new IllegalArgumentException("Cannot add yourself as contact");
        }

        if (blockRepository.findByBlockerIdAndBlockedId(userId, contactId).isPresent() ||
            blockRepository.findByBlockerIdAndBlockedId(contactId, userId).isPresent()) {
            throw new IllegalArgumentException("Cannot add a blocked user as contact");
        }

        if (contactRepository.findByUserIdAndContactId(userId, contactId).isPresent()) {
            return;
        }

        contactRepository.save(createContact(userId, contactId));
    }

    @Transactional
    public void removeContact(UUID userId, UUID contactId) {
        contactRepository.deleteByUserIdAndContactId(userId, contactId);
    }

    @Transactional
    public void blockUser(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }

        if (blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).isPresent()) {
            return;
        }

        blockRepository.save(createBlock(blockerId, blockedId));
    }

    @Transactional
    public void unblockUser(UUID blockerId, UUID blockedId) {
        blockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId)
                .ifPresent(blockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<ContactDto> getMyContacts(UUID userId) {
        return contactRepository.findAllByUserId(userId).stream()
                .map(c -> {
                    ContactDto dto = new ContactDto();
                    dto.setContactId(c.getContactId());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ContactDto> getMyBlockedUsers(UUID userId) {
        return blockRepository.findAllByBlockerId(userId).stream()
                .map(block -> {
                    ContactDto dto = new ContactDto();
                    dto.setContactId(block.getBlockedId());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public boolean existsByUserId(UUID userId) {
        return profileRepository.existsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public UserSettingsDto getMySettings(UUID userId) {
        UserSettings settings = getSettings(userId);
        return mapToSettingsDto(settings);
    }

    @Transactional
    public void updateMySettings(UUID userId, UserSettingsDto updated) {
        UserSettings settings = getSettings(userId);
        if (updated.getProfilePhoto() != null) settings.setProfilePhoto(updated.getProfilePhoto());
        if (updated.getAbout() != null) settings.setAbout(updated.getAbout());
        if (updated.getLastSeenAndOnline() != null) settings.setLastSeenAndOnline(updated.getLastSeenAndOnline());
        settings.setReadReceipts(updated.isReadReceipts());
        if (updated.getDefaultStoryVisibility() != null) settings.setDefaultStoryVisibility(updated.getDefaultStoryVisibility());
        settingsRepository.save(settings);
    }

    // ──────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────

    /**
     * Resolves the avatar display URL from the Media module.
     * Returns null if no avatar is set.
     */
    private String resolveAvatarUrl(UUID avatarMediaId) {
        if (avatarMediaId == null) {
            return null;
        }
        try {
            return mediaService.getDisplayUrl(avatarMediaId, 150, 150);
        } catch (Exception e) {
            return null; // Media deleted or expired — return no avatar
        }
    }

    private Profile getProfileEntity(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    private UserSettings getSettings(UUID userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
    }

    private boolean isMutualContact(UUID userId1, UUID userId2) {
        boolean user1HasUser2 = contactRepository.findByUserIdAndContactId(userId1, userId2).isPresent();
        boolean user2HasUser1 = contactRepository.findByUserIdAndContactId(userId2, userId1).isPresent();
        return user1HasUser2 && user2HasUser1;
    }

    private Contact createContact(UUID userId, UUID contactId) {
        Contact c = new Contact();
        c.setUserId(userId);
        c.setContactId(contactId);
        return c;
    }

    private Block createBlock(UUID blockerId, UUID blockedId) {
        Block b = new Block();
        b.setBlockerId(blockerId);
        b.setBlockedId(blockedId);
        return b;
    }

    private UserSettings createDefaultSettings(UUID userId) {
        UserSettings s = new UserSettings();
        s.setUserId(userId);
        s.setProfilePhoto(PrivacyLevel.EVERYONE);
        s.setAbout(PrivacyLevel.EVERYONE);
        s.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
        s.setReadReceipts(true);
        s.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        return settingsRepository.save(s);
    }

    private UserSettingsDto mapToSettingsDto(UserSettings settings) {
        UserSettingsDto dto = new UserSettingsDto();
        dto.setProfilePhoto(settings.getProfilePhoto());
        dto.setAbout(settings.getAbout());
        dto.setLastSeenAndOnline(settings.getLastSeenAndOnline());
        dto.setReadReceipts(settings.isReadReceipts());
        dto.setDefaultStoryVisibility(settings.getDefaultStoryVisibility());
        return dto;
    }
}