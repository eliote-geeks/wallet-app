package com.socialwallet.stories.service;

import com.socialwallet.profiles.model.Contact;
import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.repository.BlockRepository;
import com.socialwallet.profiles.repository.ContactRepository;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import com.socialwallet.stories.dto.*;
import com.socialwallet.stories.model.MediaType;
import com.socialwallet.stories.model.Story;
import com.socialwallet.stories.repository.StoryHiddenFromRepository;
import com.socialwallet.stories.repository.StoryRepository;
import com.socialwallet.stories.repository.StorySharedWithRepository;
import com.socialwallet.stories.repository.StoryViewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StoryService - Unit Tests")
class StoryServiceTest {

    @Mock private StoryRepository storyRepository;
    @Mock private StoryViewRepository storyViewRepository;
    @Mock private ProfileRepository profileRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private StoryHiddenFromRepository hiddenFromRepository;
    @Mock private StorySharedWithRepository sharedWithRepository;

    @InjectMocks private StoryService storyService;

    private final UUID authorId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID viewerId = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID storyId = UUID.fromString("00000000-0000-0000-0000-000000000099");

    private UserSettings createDefaultSettings(UUID userId) {
        UserSettings settings = new UserSettings();
        settings.setUserId(userId);
        settings.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        return settings;
    }

    private Story createImageStory(UUID authorId, PrivacyLevel visibility) {
        Story story = new Story();
        story.setId(storyId);
        story.setAuthorId(authorId);
        story.setMediaType(MediaType.IMAGE);
        story.setMediaUrl("https://cdn.example.com/photo.jpg");
        story.setCaption("Beautiful sunset");
        story.setVisibility(visibility);
        story.setCreatedAt(LocalDateTime.now());
        story.setExpiresAt(LocalDateTime.now().plusHours(24));
        return story;
    }

    @Nested
    @DisplayName("Story Creation Tests")
    class StoryCreation {

        @Test
        @DisplayName("createStory with IMAGE type succeeds with valid mediaUrl")
        void createStory_imageType_succeeds() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.IMAGE);
            request.setMediaUrl("https://cdn.example.com/photo.jpg");
            request.setCaption("Test caption");

            UserSettings settings = createDefaultSettings(authorId);
            when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));
            when(storyRepository.save(any(Story.class))).thenAnswer(i -> {
                Story s = i.getArgument(0);
                s.setId(storyId);
                s.setCreatedAt(LocalDateTime.now());
                s.setExpiresAt(LocalDateTime.now().plusHours(24));
                return s;
            });

            StoryDto result = storyService.createStory(authorId, request);

            assertNotNull(result);
            assertEquals(MediaType.IMAGE, result.getMediaType());
            assertEquals("https://cdn.example.com/photo.jpg", result.getMediaUrl());
            assertEquals("Test caption", result.getCaption());
            verify(storyRepository).save(any(Story.class));
        }

        @Test
        @DisplayName("createStory with TEXT type requires textContent and backgroundColor")
        void createStory_textType_requiresTextAndColor() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.TEXT);
            request.setTextContent("Hello World");
            request.setBackgroundColor("#FF5733");

            UserSettings settings = createDefaultSettings(authorId);
            when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));
            when(storyRepository.save(any(Story.class))).thenAnswer(i -> {
                Story s = i.getArgument(0);
                s.setId(storyId);
                s.setCreatedAt(LocalDateTime.now());
                s.setExpiresAt(LocalDateTime.now().plusHours(24));
                return s;
            });

            StoryDto result = storyService.createStory(authorId, request);

            assertEquals(MediaType.TEXT, result.getMediaType());
            assertEquals("Hello World", result.getTextContent());
            assertEquals("#FF5733", result.getBackgroundColor());
        }

        @Test
        @DisplayName("createStory throws when IMAGE has no mediaUrl")
        void createStory_imageWithoutUrl_throws() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.IMAGE);

            UserSettings settings = createDefaultSettings(authorId);
            lenient().when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));

            assertThrows(IllegalArgumentException.class, () ->
                storyService.createStory(authorId, request)
            );
        }

        @Test
        @DisplayName("createStory throws when TEXT has no textContent")
        void createStory_textWithoutContent_throws() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.TEXT);
            request.setBackgroundColor("#FF5733");

            UserSettings settings = createDefaultSettings(authorId);
            lenient().when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));

            assertThrows(IllegalArgumentException.class, () ->
                storyService.createStory(authorId, request)
            );
        }

        @Test
        @DisplayName("createStory uses default visibility from UserSettings")
        void createStory_usesDefaultVisibility() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.IMAGE);
            request.setMediaUrl("https://cdn.example.com/photo.jpg");
            // No visibility specified

            UserSettings settings = createDefaultSettings(authorId);
            settings.setDefaultStoryVisibility(PrivacyLevel.EVERYONE);
            when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));
            when(storyRepository.save(any(Story.class))).thenAnswer(i -> {
                Story s = i.getArgument(0);
                s.setId(storyId);
                s.setCreatedAt(LocalDateTime.now());
                s.setExpiresAt(LocalDateTime.now().plusHours(24));
                return s;
            });

            StoryDto result = storyService.createStory(authorId, request);

            assertEquals(PrivacyLevel.EVERYONE, result.getVisibility());
        }

        @Test
        @DisplayName("createStory throws when visibility is NOBODY")
        void createStory_visibilityNobody_throws() {
            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(MediaType.IMAGE);
            request.setMediaUrl("https://cdn.example.com/photo.jpg");

            UserSettings settings = createDefaultSettings(authorId);
            settings.setDefaultStoryVisibility(PrivacyLevel.NOBODY);
            when(userSettingsRepository.findByUserId(authorId)).thenReturn(Optional.of(settings));

            assertThrows(IllegalArgumentException.class, () ->
                storyService.createStory(authorId, request)
            );
        }
    }

    @Nested
    @DisplayName("Story Retrieval Tests")
    class StoryRetrieval {

        @Test
        @DisplayName("getMyActiveStories returns all non-expired stories with view counts")
        void getMyActiveStories_returnsActiveStories() {
            Story story1 = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            Story story2 = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findByAuthorIdAndExpiresAtAfterOrderByCreatedAtDesc(eq(authorId), any()))
                .thenReturn(List.of(story1, story2));
            when(storyViewRepository.countByStoryId(any())).thenReturn(5L);

            List<StoryDto> results = storyService.getMyActiveStories(authorId);

            assertEquals(2, results.size());
            assertEquals(5, results.get(0).getViewCount());
        }

        @Test
        @DisplayName("getStory throws when story not found")
        void getStory_notFound_throws() {
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () ->
                storyService.getStory(viewerId, storyId)
            );
        }

        @Test
        @DisplayName("getStory throws when viewer not authorized (not mutual contact)")
        void getStory_notAuthorized_throws() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.of(story));
            
            // Not mutual contact (only one direction)
            when(contactRepository.findByUserIdAndContactId(viewerId, authorId))
                .thenReturn(Optional.empty());
            when(contactRepository.findByUserIdAndContactId(authorId, viewerId))
                .thenReturn(Optional.empty());
            
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any()))
                .thenReturn(Optional.empty());
            
            when(sharedWithRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());
            when(hiddenFromRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());

            assertThrows(IllegalArgumentException.class, () ->
                storyService.getStory(viewerId, storyId)
            );
        }

        @Test
        @DisplayName("getStory succeeds when viewer is mutual contact")
        void getStory_mutualContact_succeeds() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.of(story));
            
            // Mock BOTH directions for mutual contact
            when(contactRepository.findByUserIdAndContactId(viewerId, authorId))
                .thenReturn(Optional.of(new Contact()));
            when(contactRepository.findByUserIdAndContactId(authorId, viewerId))
                .thenReturn(Optional.of(new Contact()));
            
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any()))
                .thenReturn(Optional.empty());
            when(storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId))
                .thenReturn(Optional.empty());
            
            when(sharedWithRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());
            when(hiddenFromRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());

            StoryDto result = storyService.getStory(viewerId, storyId);

            assertNotNull(result);
            assertEquals(storyId, result.getStoryId());
        }
    }

    @Nested
    @DisplayName("Story View Tests")
    class StoryViewTests {

        @Test
        @DisplayName("markAsViewed creates view when not already viewed")
        void markAsViewed_createsView() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.of(story));
            
            // Mock BOTH directions for mutual contact
            when(contactRepository.findByUserIdAndContactId(viewerId, authorId))
                .thenReturn(Optional.of(new Contact()));
            when(contactRepository.findByUserIdAndContactId(authorId, viewerId))
                .thenReturn(Optional.of(new Contact()));
            
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any()))
                .thenReturn(Optional.empty());
            when(storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId))
                .thenReturn(Optional.empty());
            
            when(sharedWithRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());
            when(hiddenFromRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());

            storyService.markAsViewed(viewerId, storyId);

            verify(storyViewRepository).save(any(com.socialwallet.stories.model.StoryView.class));
        }

        @Test
        @DisplayName("markAsViewed is idempotent")
        void markAsViewed_isIdempotent() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            com.socialwallet.stories.model.StoryView existingView = new com.socialwallet.stories.model.StoryView();
            
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.of(story));
            
            // Mock BOTH directions for mutual contact
            when(contactRepository.findByUserIdAndContactId(viewerId, authorId))
                .thenReturn(Optional.of(new Contact()));
            when(contactRepository.findByUserIdAndContactId(authorId, viewerId))
                .thenReturn(Optional.of(new Contact()));
            
            when(blockRepository.findByBlockerIdAndBlockedId(any(), any()))
                .thenReturn(Optional.empty());
            when(storyViewRepository.findByStoryIdAndViewerId(storyId, viewerId))
                .thenReturn(Optional.of(existingView));
            
            when(sharedWithRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());
            when(hiddenFromRepository.findByStoryId(storyId))
                .thenReturn(Collections.emptyList());

            storyService.markAsViewed(viewerId, storyId);

            verify(storyViewRepository, never()).save(any());
        }

        @Test
        @DisplayName("markAsViewed does not record view for author viewing own story")
        void markAsViewed_authorOwnStory_noRecord() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findByIdAndExpiresAtAfter(eq(storyId), any()))
                .thenReturn(Optional.of(story));

            storyService.markAsViewed(authorId, storyId);

            verify(storyViewRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Story Deletion Tests")
    class StoryDeletion {

        @Test
        @DisplayName("deleteStory succeeds when author deletes own story")
        void deleteStory_authorDeletes_succeeds() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

            storyService.deleteStory(authorId, storyId);

            verify(hiddenFromRepository).deleteByStoryId(storyId);
            verify(sharedWithRepository).deleteByStoryId(storyId);
            verify(storyViewRepository).deleteByStoryId(storyId);
            verify(storyRepository).delete(story);
        }

        @Test
        @DisplayName("deleteStory throws when non-author tries to delete")
        void deleteStory_nonAuthor_throws() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

            assertThrows(IllegalArgumentException.class, () ->
                storyService.deleteStory(viewerId, storyId)
            );
        }
    }

    @Nested
    @DisplayName("Story Viewers Tests")
    class StoryViewersTests {

        @Test
        @DisplayName("getStoryViewers returns all viewers for author")
        void getStoryViewers_author_returnsViewers() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            com.socialwallet.stories.model.StoryView view1 = new com.socialwallet.stories.model.StoryView();
            view1.setViewerId(viewerId);
            view1.setViewedAt(LocalDateTime.now());
            
            Profile viewerProfile = new Profile();
            viewerProfile.setUserId(viewerId);
            viewerProfile.setName("Viewer");
            
            when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));
            when(storyViewRepository.findByStoryIdOrderByViewedAtDesc(storyId))
                .thenReturn(List.of(view1));
            when(profileRepository.findByUserId(viewerId))
                .thenReturn(Optional.of(viewerProfile));

            List<StoryViewDto> viewers = storyService.getStoryViewers(authorId, storyId);

            assertEquals(1, viewers.size());
            assertEquals(viewerId, viewers.get(0).getViewerId());
            assertEquals("Viewer", viewers.get(0).getViewerName());
        }

        @Test
        @DisplayName("getStoryViewers throws when non-author tries to access")
        void getStoryViewers_nonAuthor_throws() {
            Story story = createImageStory(authorId, PrivacyLevel.MY_CONTACTS);
            
            when(storyRepository.findById(storyId)).thenReturn(Optional.of(story));

            assertThrows(IllegalArgumentException.class, () ->
                storyService.getStoryViewers(viewerId, storyId)
            );
        }
    }
}