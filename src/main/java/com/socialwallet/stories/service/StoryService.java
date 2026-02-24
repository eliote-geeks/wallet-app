package com.socialwallet.stories.service;

import com.socialwallet.media.service.MediaService;
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
    private final MediaService mediaService;

    @Transactional
    public StoryDto createStory(UUID authorId, PostStoryRequest request) {
        validateStoryRequest(request);

        PrivacyLevel visibility = request.getVisibility();
        if (visibility == null) {
            UserSettings settings = userSettingsRepository.findByUserId(authorId)
                .orElseThrow(() -> new IllegalArgumentException("User settings not found"));
            visibility = settings.getDefaultStoryVisibility();
        }

        if (visibility == PrivacyLevel.NOBODY) {
            throw new IllegalArgumentException("Stories are disabled in your privacy settings");
        }

        Story story = new Story();
        story.setAuthorId(authorId);
        story.setMediaType(request.getMediaType());
        story.setMediaId(request.getMediaId());
        story.setCaption(request.getCaption());
        story.setTextContent(request.getTextContent());
        story.setBackgroundColor(request.getBackgroundColor());
        story.setVisibility(visibility);

        Story saved = storyRepository.save(story);

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
        List<UUID> contactIds = contactRepository.findAllByUserId(viewerId).stream()
            .map(contact -> contact.getContactId())
            .collect(Collectors.toList());

        if (contactIds.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> blockedUsers = getBlockedUsersSet(viewerId);
        contactIds.removeAll(blockedUsers);

        if (contactIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Story> allStories = storyRepository.findActiveStoriesByAuthors(contactIds, LocalDateTime.now());

        List<Story> visibleStories = allStories.stream()
            .filter(story -> canViewStory(viewerId, story))
            .collect(Collectors.toList());

        List<UUID> storyIds = visibleStories.stream().map(Story::getId).collect(Collectors.toList());
        Set<UUID> viewedStoryIds = storyViewRepository.findByStoryIdInAndViewerId(storyIds, viewerId).stream()
            .map(StoryView::getStoryId)
            .collect(Collectors.toSet());

        Map<UUID, List<Story>> storiesByAuthor = visibleStories.stream()
            .collect(Collectors.groupingBy(Story::getAuthorId));

        return storiesByAuthor.entrySet().stream()
            .map(entry -> {
                UUID authorId = entry.getKey();
                List<Story> authorStories = entry.getValue();

                Profile authorProfile = profileRepository.findByUserId(authorId).orElse(null);

                StoryPreviewDto preview = new StoryPreviewDto();
                preview.setAuthorId(authorId);
                preview.setAuthorName(authorProfile != null ? authorProfile.getName() : "Unknown");
                preview.setAuthorPhotoUrl(resolveAvatarUrl(authorProfile));

                List<StoryItemDto> items = authorStories.stream()
                    .map(story -> mapToStoryItemDto(story, viewedStoryIds.contains(story.getId())))
                    .collect(Collectors.toList());

                preview.setStories(items);
                preview.setUnviewedCount((int) items.stream().filter(item -> !item.isViewedByMe()).count());
                preview.setLastStoryAt(authorStories.get(0).getCreatedAt());

                return preview;
            })
            .sorted((a, b) -> {
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

        if (story.getAuthorId().equals(viewerId)) {
            return;
        }

        if (!canViewStory(viewerId, story)) {
            throw new IllegalArgumentException("You are not authorized to view this story");
        }

        if (storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId).isEmpty()) {
            StoryView view = new StoryView();
            view.setStoryId(storyId);
            view.setViewerId(viewerId);
            storyViewRepository.save(view);
            log.info("Story view recorded: storyId={}, viewerId={}", storyId, viewerId);
        }
    }

    @Transactional
    public void deleteStory(UUID authorId, UUID storyId) {
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new IllegalArgumentException("Story not found"));

        if (!story.getAuthorId().equals(authorId)) {
            throw new IllegalArgumentException("You can only delete your own stories");
        }

        hiddenFromRepository.deleteByStoryId(storyId);
        sharedWithRepository.deleteByStoryId(storyId);
        storyViewRepository.deleteByStoryId(storyId);
        storyRepository.delete(story);
        log.info("Story deleted: id={}, authorId={}", storyId, authorId);
    }

    @Transactional(readOnly = true)
    public List<StoryViewDto> getStoryViewers(UUID authorId, UUID storyId) {
        Story story = storyRepository.findById(storyId)
            .orElseThrow(() -> new IllegalArgumentException("Story not found"));

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
                dto.setViewerPhotoUrl(resolveAvatarUrl(viewerProfile));
                dto.setViewedAt(view.getViewedAt());
                return dto;
            })
            .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────
    // PRIVATE HELPERS
    // ──────────────────────────────────────────────

    private void validateStoryRequest(PostStoryRequest request) {
        MediaType type = request.getMediaType();

        if (type == MediaType.IMAGE || type == MediaType.VIDEO) {
            if (request.getMediaId() == null) {
                throw new IllegalArgumentException("Media ID is required for " + type + " stories");
            }
            if (!mediaService.exists(request.getMediaId())) {
                throw new IllegalArgumentException("Media not found: " + request.getMediaId());
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
     * Resolve the display URL for a media asset via the Media module.
     * Returns null if no media is associated.
     */
    private String resolveMediaUrl(UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        try {
            return mediaService.getDisplayUrl(mediaId);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Resolve the avatar display URL from a Profile's avatarMediaId.
     * Returns null if profile is null or has no avatar.
     */
    private String resolveAvatarUrl(Profile profile) {
        if (profile == null || profile.getAvatarMediaId() == null) {
            return null;
        }
        try {
            return mediaService.getDisplayUrl(profile.getAvatarMediaId(), 150, 150);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean canViewStory(UUID viewerId, Story story) {
        UUID authorId = story.getAuthorId();

        if (authorId.equals(viewerId)) {
            return true;
        }

        if (blockRepository.findByBlockerIdAndBlockedId(viewerId, authorId).isPresent() ||
            blockRepository.findByBlockerIdAndBlockedId(authorId, viewerId).isPresent()) {
            return false;
        }

        List<StorySharedWith> sharedWithList = sharedWithRepository.findByStoryId(story.getId());
        if (!sharedWithList.isEmpty()) {
            return sharedWithList.stream()
                .anyMatch(sw -> sw.getSharedUserId().equals(viewerId));
        }

        List<StoryHiddenFrom> hiddenFromList = hiddenFromRepository.findByStoryId(story.getId());
        boolean isHidden = hiddenFromList.stream()
            .anyMatch(hf -> hf.getHiddenUserId().equals(viewerId));
        if (isHidden) {
            return false;
        }

        if (story.getVisibility() == PrivacyLevel.EVERYONE) {
            return true;
        }

        if (story.getVisibility() == PrivacyLevel.MY_CONTACTS) {
            boolean viewerHasAuthor = contactRepository.findByUserIdAndContactId(viewerId, authorId).isPresent();
            boolean authorHasViewer = contactRepository.findByUserIdAndContactId(authorId, viewerId).isPresent();
            return viewerHasAuthor && authorHasViewer;
        }

        return false;
    }

    private Set<UUID> getBlockedUsersSet(UUID userId) {
        Set<UUID> blocked = new HashSet<>();

        blockRepository.findAllByBlockerId(userId).forEach(block ->
            blocked.add(block.getBlockedId())
        );

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
        dto.setMediaId(story.getMediaId());
        dto.setMediaUrl(resolveMediaUrl(story.getMediaId()));
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
        dto.setMediaId(story.getMediaId());
        dto.setMediaUrl(resolveMediaUrl(story.getMediaId()));
        dto.setCaption(story.getCaption());
        dto.setTextContent(story.getTextContent());
        dto.setBackgroundColor(story.getBackgroundColor());
        dto.setCreatedAt(story.getCreatedAt());
        dto.setExpiresAt(story.getExpiresAt());
        dto.setViewedByMe(viewedByMe);
        return dto;
    }
}