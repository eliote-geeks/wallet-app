package com.socialwallet.stories.service;

import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.repository.BlockRepository;
import com.socialwallet.profiles.repository.ContactRepository;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import com.socialwallet.stories.dto.*;
import com.socialwallet.stories.model.*;
import com.socialwallet.stories.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StoryService {

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final ProfileRepository profileRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ContactRepository contactRepository;
    private final BlockRepository blockRepository;
    private final StoryHiddenFromRepository hiddenFromRepository;
    private final StorySharedWithRepository sharedWithRepository;

    @Transactional
    public StoryDto createStory(UUID authorId, PostStoryRequest request) {
        // Validate media type requirements
        validateStoryRequest(request);

        // Get user's default story visibility if not specified
        PrivacyLevel visibility = request.getVisibility();
        if (visibility == null) {
            UserSettings settings = userSettingsRepository.findByUserId(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
            visibility = settings.getDefaultStoryVisibility();
        }

        // Check if user disabled stories
        if (visibility == PrivacyLevel.NOBODY) {
            throw new IllegalArgumentException("Stories are disabled in your privacy settings");
        }

        Story story = new Story();
        story.setAuthorId(authorId);
        story.setMediaType(request.getMediaType());
        story.setMediaUrl(request.getMediaUrl());
        story.setCaption(request.getCaption());
        story.setTextContent(request.getTextContent());
        story.setBackgroundColor(request.getBackgroundColor());
        story.setVisibility(visibility);
        // createdAt and expiresAt set by @PrePersist

        Story saved = storyRepository.save(story);

        // Save privacy lists if provided
        if (request.getHiddenFrom() != null && !request.getHiddenFrom().isEmpty()) {
            for (UUID hiddenUserId : request.getHiddenFrom()) {
                StoryHiddenFrom hidden = new StoryHiddenFrom();
                hidden.setStoryId(saved.getId());
                hidden.setHiddenUserId(hiddenUserId);
                hiddenFromRepository.save(hidden);
            }
            log.info("Story hidden from {} users", request.getHiddenFrom().size());
        }

        if (request.getSharedWith() != null && !request.getSharedWith().isEmpty()) {
            for (UUID sharedUserId : request.getSharedWith()) {
                StorySharedWith shared = new StorySharedWith();
                shared.setStoryId(saved.getId());
                shared.setSharedUserId(sharedUserId);
                sharedWithRepository.save(shared);
            }
            log.info("Story shared with {} users only", request.getSharedWith().size());
        }

        log.info("Story created: id={}, authorId={}, type={}", saved.getId(), authorId, request.getMediaType());
        return mapToStoryDto(saved, authorId, false);
    }

    @Transactional(readOnly = true)
    public List<StoryDto> getMyActiveStories(UUID authorId) {
        List<Story> stories = storyRepository.findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(
            authorId, LocalDateTime.now()
        );

        return stories.stream()
            .map(story -> {
                int viewCount = (int) storyViewRepository.countByStoryId(story.getId());
                StoryDto dto = mapToStoryDto(story, authorId, false);
                dto.setViewCount(viewCount);
                return dto;
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StoryPreviewDto> getContactsStories(UUID viewerId) {
        // Get all mutual contacts
        List<UUID> contactIds = contactRepository.findAllByUserId(viewerId).stream()
            .map(contact -> contact.getContactId())
            .collect(Collectors.toList());

        if (contactIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Get blocked users (both directions)
        Set<UUID> blockedUsers = getBlockedUsersSet(viewerId);

        // Remove blocked users from contacts
        contactIds.removeAll(blockedUsers);

        if (contactIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all active stories from contacts
        List<Story> allStories = storyRepository.findActiveStoriesByAuthors(contactIds, LocalDateTime.now());

        // Filter by visibility rules
        List<Story> visibleStories = allStories.stream()
            .filter(story -> canViewStory(viewerId, story))
            .collect(Collectors.toList());

        // Get viewer's views
        List<UUID> storyIds = visibleStories.stream().map(Story::getId).collect(Collectors.toList());
        Set<UUID> viewedStoryIds = storyViewRepository.findByStoryIdInAndViewerId(storyIds, viewerId).stream()
            .map(StoryView::getStoryId)
            .collect(Collectors.toSet());

        // Group by author
        Map<UUID, List<Story>> storiesByAuthor = visibleStories.stream()
            .collect(Collectors.groupingBy(Story::getAuthorId));

        // Build preview DTOs
        return storiesByAuthor.entrySet().stream()
            .map(entry -> {
                UUID authorId = entry.getKey();
                List<Story> authorStories = entry.getValue();

                Profile authorProfile = profileRepository.findByUserId(authorId).orElse(null);

                StoryPreviewDto preview = new StoryPreviewDto();
                preview.setAuthorId(authorId);
                preview.setAuthorName(authorProfile != null ? authorProfile.getName() : "Unknown");
                preview.setAuthorPhotoUrl(authorProfile != null ? authorProfile.getPhotoUrl() : null);

                List<StoryItemDto> items = authorStories.stream()
                    .map(story -> mapToStoryItemDto(story, viewedStoryIds.contains(story.getId())))
                    .collect(Collectors.toList());

                preview.setStories(items);
                preview.setUnviewedCount((int) items.stream().filter(item -> !item.isViewedByMe()).count());
                preview.setLastStoryAt(authorStories.get(0).getCreatedAt()); // Already sorted desc

                return preview;
            })
            .sorted((a, b) -> {
                // Sort: unviewed first, then by last story time
                if (a.getUnviewedCount() > 0 && b.getUnviewedCount() == 0) return -1;
                if (a.getUnviewedCount() == 0 && b.getUnviewedCount() > 0) return 1;
                return b.getLastStoryAt().compareTo(a.getLastStoryAt());
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StoryDto getStory(UUID viewerId, UUID storyId) {
        Story story = storyRepository.findByIdAndExpiresAtAfter(storyId, LocalDateTime.now())
            .orElseThrow(() -> new IllegalArgumentException("Story not found or expired"));

        // Check authorization
        if (!canViewStory(viewerId, story)) {
            throw new IllegalArgumentException("You are not authorized to view this story");
        }

        boolean viewedByMe = storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).isPresent();
        int viewCount = story.getAuthorId().equals(viewerId) 
            ? (int) storyViewRepository.countByStoryId(storyId) 
            : 0;

        StoryDto dto = mapToStoryDto(story, viewerId, viewedByMe);
        dto.setViewCount(viewCount);
        return dto;
    }

    @Transactional
    public void markAsViewed(UUID viewerId, UUID storyId) {
        Story story = storyRepository.findByIdAndExpiresAtAfter(storyId, LocalDateTime.now())
            .orElseThrow(() -> new IllegalArgumentException("Story not found or expired"));

        // Don't record view if author viewing own story
        if (story.getAuthorId().equals(viewerId)) {
            return;
        }

        // Check authorization
        if (!canViewStory(viewerId, story)) {
            throw new IllegalArgumentException("You are not authorized to view this story");
        }

        // Idempotent: only create if not already viewed
        if (storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).isEmpty()) {
            StoryView view = new StoryView();
            view.setStoryId(storyId);
            view.setViewerId(viewerId);
            // viewedAt set by @PrePersist
            storyViewRepository.save(view);
            log.info("Story view recorded: storyId={}, viewerId={}", storyId, viewerId);
        }
    }

    @Transactional
    public void deleteStory(UUID authorId, UUID storyId) {
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        // Only author can delete
        if (!story.getAuthorId().equals(authorId)) {
            throw new IllegalArgumentException("You can only delete your own stories");
        }

        // Delete privacy lists
        hiddenFromRepository.deleteByStoryId(storyId);
        sharedWithRepository.deleteByStoryId(storyId);

        // Delete views
        storyViewRepository.deleteByStoryId(storyId);
        
        // Delete story
        storyRepository.delete(story);
        log.info("Story deleted: id={}, authorId={}", storyId, authorId);
    }

    @Transactional(readOnly = true)
    public List<StoryViewDto> getStoryViewers(UUID authorId, UUID storyId) {
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        // Only author can see viewers
        if (!story.getAuthorId().equals(authorId)) {
            throw new IllegalArgumentException("You can only view viewers of your own stories");
        }

        List<StoryView> views = storyViewRepository.findByStoryIdOrderByViewedAtDesc(storyId);

        return views.stream()
            .map(view -> {
                Profile viewerProfile = profileRepository.findByUserId(view.getViewerId()).orElse(null);
                StoryViewDto dto = new StoryViewDto();
                dto.setViewerId(view.getViewerId());
                dto.setViewerName(viewerProfile != null ? viewerProfile.getName() : "Unknown");
                dto.setViewerPhotoUrl(viewerProfile != null ? viewerProfile.getPhotoUrl() : null);
                dto.setViewedAt(view.getViewedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }

    // Helper methods

    private void validateStoryRequest(PostStoryRequest request) {
        MediaType type = request.getMediaType();

        if (type == MediaType.IMAGE || type == MediaType.VIDEO) {
            if (request.getMediaUrl() == null || request.getMediaUrl().isBlank()) {
                throw new IllegalArgumentException("Media URL is required for " + type + " stories");
            }
        } else if (type == MediaType.TEXT) {
            if (request.getTextContent() == null || request.getTextContent().isBlank()) {
                throw new IllegalArgumentException("Text content is required for TEXT stories");
            }
            if (request.getBackgroundColor() == null || request.getBackgroundColor().isBlank()) {
                throw new IllegalArgumentException("Background color is required for TEXT stories");
            }
        }
    }

    /**
     * Checks if a viewer can see a specific story based on:
     * 1. Ownership (author can always see own stories)
     * 2. Blocks (blocked users cannot see stories)
     * 3. "Shared with" list (if present, ONLY these users can see)
     * 4. "Hidden from" list (excluded users cannot see)
     * 5. Visibility level (EVERYONE or MY_CONTACTS with mutual contact check)
     */
    private boolean canViewStory(UUID viewerId, Story story) {
        UUID authorId = story.getAuthorId();

        // Author can always see own story
        if (authorId.equals(viewerId)) {
            return true;
        }

        // Check if blocked in either direction
        if (blockRepository.findByBlockerIdAndBlockedId(viewerId, authorId).isPresent() ||
            blockRepository.findByBlockerIdAndBlockedId(authorId, viewerId).isPresent()) {
            return false;
        }

        // Check "sharedWith" list (PRIORITY: if present, ONLY these users can see)
        List<StorySharedWith> sharedWithList = sharedWithRepository.findByStoryId(story.getId());
        if (!sharedWithList.isEmpty()) {
            boolean isInSharedList = sharedWithList.stream()
                .anyMatch(sw -> sw.getSharedUserId().equals(viewerId));
            return isInSharedList;
        }

        // Check "hiddenFrom" list (excluded users)
        List<StoryHiddenFrom> hiddenFromList = hiddenFromRepository.findByStoryId(story.getId());
        boolean isHidden = hiddenFromList.stream()
            .anyMatch(hf -> hf.getHiddenUserId().equals(viewerId));
        if (isHidden) {
            return false;
        }

        // Check visibility level
        if (story.getVisibility() == PrivacyLevel.EVERYONE) {
            return true;
        }

        if (story.getVisibility() == PrivacyLevel.MY_CONTACTS) {
            // Must be MUTUAL contact (both added each other)
            boolean viewerHasAuthor = contactRepository.findByUserIdAndContactId(viewerId, authorId).isPresent();
            boolean authorHasViewer = contactRepository.findByUserIdAndContactId(authorId, viewerId).isPresent();
            return viewerHasAuthor && authorHasViewer;
        }

        // NOBODY = no one can see
        return false;
    }

    private Set<UUID> getBlockedUsersSet(UUID userId) {
        Set<UUID> blocked = new HashSet<>();

        // Users I blocked
        blockRepository.findAllByBlockerId(userId).forEach(block -> 
            blocked.add(block.getBlockedId())
        );

        // Users who blocked me (check all blocks and filter)
        // This is inefficient for large scale - consider indexing
        blockRepository.findAll().forEach(block -> {
            if (block.getBlockedId().equals(userId)) {
                blocked.add(block.getBlockerId());
            }
        });

        return blocked;
    }

    private StoryDto mapToStoryDto(Story story, UUID viewerId, boolean viewedByMe) {
        StoryDto dto = new StoryDto();
        dto.setStoryId(story.getId());
        dto.setAuthorId(story.getAuthorId());
        dto.setMediaType(story.getMediaType());
        dto.setMediaUrl(story.getMediaUrl());
        dto.setCaption(story.getCaption());
        dto.setTextContent(story.getTextContent());
        dto.setBackgroundColor(story.getBackgroundColor());
        dto.setCreatedAt(story.getCreatedAt());
        dto.setExpiresAt(story.getExpiresAt());
        dto.setVisibility(story.getVisibility());
        dto.setViewedByMe(viewedByMe);
        return dto;
    }

    private StoryItemDto mapToStoryItemDto(Story story, boolean viewedByMe) {
        StoryItemDto dto = new StoryItemDto();
        dto.setStoryId(story.getId());
        dto.setMediaType(story.getMediaType());
        dto.setMediaUrl(story.getMediaUrl());
        dto.setCaption(story.getCaption());
        dto.setTextContent(story.getTextContent());
        dto.setBackgroundColor(story.getBackgroundColor());
        dto.setCreatedAt(story.getCreatedAt());
        dto.setExpiresAt(story.getExpiresAt());
        dto.setViewedByMe(viewedByMe);
        return dto;
    }
}