package com.socialwallet.profiles.service;

import com.socialwallet.profiles.dto.*;
import com.socialwallet.profiles.model.*;
import com.socialwallet.profiles.repository.*;
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

    @Transactional(readOnly = true)
    public MyProfileDto getMyProfile(UUID userId) {
        Profile profile = getProfileEntity(userId);
        MyProfileDto dto = new MyProfileDto();
        dto.setUserId(profile.getUserId());
        dto.setName(profile.getName());
        dto.setAbout(profile.getAbout());
        dto.setPhotoUrl(profile.getPhotoUrl());
        return dto;
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfileForViewer(UUID viewerId, UUID targetId) {
        Profile profile = getProfileEntity(targetId);
        UserSettings settings = getSettings(targetId);
        boolean isMutualContact = isMutualContact(viewerId, targetId);  // ← RENOMMÉ pour clarté

        ProfileDto dto = new ProfileDto();
        dto.setUserId(profile.getUserId());
        dto.setName(profile.getName());

        // Photo visible si EVERYONE OU (MY_CONTACTS et contact mutuel)
        if (settings.getProfilePhoto() == PrivacyLevel.EVERYONE ||
            (settings.getProfilePhoto() == PrivacyLevel.MY_CONTACTS && isMutualContact)) {
            dto.setPhotoUrl(profile.getPhotoUrl());
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
        if (updated.getPhotoUrl() != null) {
            profile.setPhotoUrl(updated.getPhotoUrl());
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
            return; // Idempotent
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
            return; // Idempotent
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

    /**
     * Checks if a user with the given userId exists in the system.
     *
     * @param userId the UUID to check
     * @return true if the user exists (has a profile), false otherwise
     */
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

    private Profile getProfileEntity(UUID userId) {
        return profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    private UserSettings getSettings(UUID userId) {
        return settingsRepository.findByUserId(userId)
                .orElseGet(() -> createDefaultSettings(userId));
    }

    /**
     * Checks if two users are MUTUAL contacts.
     * Returns true ONLY if BOTH users have added each other.
     * 
     * @param userId1 First user ID
     * @param userId2 Second user ID
     * @return true if both have added each other, false otherwise
     */
    private boolean isMutualContact(UUID userId1, UUID userId2) {
        boolean user1HasUser2 = contactRepository.findByUserIdAndContactId(userId1, userId2).isPresent();
        boolean user2HasUser1 = contactRepository.findByUserIdAndContactId(userId2, userId1).isPresent();
        return user1HasUser2 && user2HasUser1;  // ← LES DEUX doivent être vrais
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