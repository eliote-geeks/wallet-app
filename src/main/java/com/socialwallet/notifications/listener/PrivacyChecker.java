package com.socialwallet.notifications.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Verifies privacy rules before sending notifications.
 * Uses Profiles module repositories directly (monolith, same JVM).
 *
 * TODO: Wire in actual BlockRepository and ContactRepository from Profiles module.
 * For now, provides the interface and defaults to allowing notifications.
 */
@Slf4j
@Component
public class PrivacyChecker {

    // TODO: Inject from Profiles module
    // private final BlockRepository blockRepository;
    // private final ContactRepository contactRepository;

    /**
     * Check if blockerId has blocked blockedId.
     */
    public boolean isBlocked(UUID blockerId, UUID blockedId) {
        // TODO: return blockRepository.existsByBlockerIdAndBlockedId(blockerId, blockedId);
        return false;
    }

    /**
     * Check if two users are mutual contacts (both have added each other).
     */
    public boolean areMutualContacts(UUID userA, UUID userB) {
        // TODO: return contactRepository.existsByOwnerIdAndContactId(userA, userB)
        //    && contactRepository.existsByOwnerIdAndContactId(userB, userA);
        return true;
    }

    /**
     * Full story privacy check: mutual contacts AND no blocking in either direction.
     */
    public boolean canViewStory(UUID viewerId, UUID authorId) {
        return areMutualContacts(viewerId, authorId)
                && !isBlocked(authorId, viewerId)
                && !isBlocked(viewerId, authorId);
    }
}
