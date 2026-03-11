package com.socialwallet.stories.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialwallet.media.model.MediaAsset;
import com.socialwallet.media.model.MediaPurpose;
import com.socialwallet.media.model.MediaStatus;
import com.socialwallet.media.repository.MediaAssetRepository;
import com.socialwallet.profiles.model.Contact;
import com.socialwallet.profiles.model.PrivacyLevel;
import com.socialwallet.profiles.model.Profile;
import com.socialwallet.profiles.model.UserSettings;
import com.socialwallet.profiles.repository.ContactRepository;
import com.socialwallet.profiles.repository.ProfileRepository;
import com.socialwallet.profiles.repository.UserSettingsRepository;
import com.socialwallet.stories.dto.PostStoryRequest;
import com.socialwallet.stories.model.Story;
import com.socialwallet.stories.repository.StoryRepository;
import com.socialwallet.stories.repository.StoryViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("StoryController - Integration Tests")
class StoryControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private StoryRepository storyRepository;
    @Autowired private StoryViewRepository storyViewRepository;
    @Autowired private ProfileRepository profileRepository;
    @Autowired private UserSettingsRepository userSettingsRepository;
    @Autowired private ContactRepository contactRepository;
    @Autowired private MediaAssetRepository mediaAssetRepository;

    private final UUID authenticatedUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID contactUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        storyViewRepository.deleteAll();
        storyRepository.deleteAll();
        contactRepository.deleteAll();
        userSettingsRepository.deleteAll();
        profileRepository.deleteAll();
        mediaAssetRepository.deleteAll();
    }

    private Profile createAndSaveProfile(UUID userId, String name) {
        Profile p = new Profile();
        p.setUserId(userId);
        p.setName(name);
        p.setAbout("Test about");
        return profileRepository.save(p);
    }

    private UserSettings createAndSaveSettings(UUID userId) {
        UserSettings s = new UserSettings();
        s.setUserId(userId);
        s.setDefaultStoryVisibility(PrivacyLevel.MY_CONTACTS);
        s.setProfilePhoto(PrivacyLevel.EVERYONE);
        s.setAbout(PrivacyLevel.EVERYONE);
        s.setLastSeenAndOnline(PrivacyLevel.MY_CONTACTS);
        s.setReadReceipts(true);
        return userSettingsRepository.save(s);
    }

    /**
     * Creates a media asset in the database to use as story media reference.
     */
    private MediaAsset createAndSaveMedia(UUID uploaderId) {
        MediaAsset asset = MediaAsset.builder()
                .uploaderId(uploaderId)
                .purpose(MediaPurpose.STORY)
                .originalFilename("story-photo.jpg")
                .mimeType("image/jpeg")
                .sizeBytes(2048L)
                .storagePath("story/" + uploaderId + "/" + UUID.randomUUID() + ".jpg")
                .status(MediaStatus.UPLOADED)
                .createdAt(Instant.now())
                .build();
        return mediaAssetRepository.save(asset);
    }

    private Story createAndSaveStory(UUID authorId, com.socialwallet.stories.model.MediaType mediaType, UUID mediaId) {
        Story s = new Story();
        s.setAuthorId(authorId);
        s.setMediaType(mediaType);
        s.setMediaId(mediaType == com.socialwallet.stories.model.MediaType.IMAGE ? mediaId : null);
        s.setTextContent(mediaType == com.socialwallet.stories.model.MediaType.TEXT ? "Hello" : null);
        s.setBackgroundColor(mediaType == com.socialwallet.stories.model.MediaType.TEXT ? "#FF5733" : null);
        s.setVisibility(PrivacyLevel.MY_CONTACTS);
        s.setCreatedAt(LocalDateTime.now());
        s.setExpiresAt(LocalDateTime.now().plusHours(24));
        return storyRepository.save(s);
    }

    private void createMutualContact(UUID userId1, UUID userId2) {
        Contact c1 = new Contact();
        c1.setUserId(userId1);
        c1.setContactId(userId2);
        contactRepository.save(c1);

        Contact c2 = new Contact();
        c2.setUserId(userId2);
        c2.setContactId(userId1);
        contactRepository.save(c2);
    }

    @Nested
    @DisplayName("Security Tests")
    class Security {

        @Test
        @DisplayName("All endpoints require authentication")
        void allEndpoints_requireAuthentication() throws Exception {
            mockMvc.perform(post("/api/stories")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/stories/me")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/stories/contacts")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/api/stories/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/api/stories/" + UUID.randomUUID() + "/view")).andExpect(status().isUnauthorized());
            mockMvc.perform(delete("/api/stories/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Story Creation Tests")
    class StoryCreation {

        @Test
        @DisplayName("POST / creates IMAGE story successfully")
        void createStory_image_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(authenticatedUserId);

            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(com.socialwallet.stories.model.MediaType.IMAGE);
            request.setMediaId(media.getId());
            request.setCaption("Sunset");

            mockMvc.perform(post("/api/stories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("IMAGE"))
                .andExpect(jsonPath("$.mediaId").value(media.getId().toString()))
                .andExpect(jsonPath("$.caption").value("Sunset"));
        }

        @Test
        @DisplayName("POST / creates TEXT story successfully")
        void createStory_text_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);

            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(com.socialwallet.stories.model.MediaType.TEXT);
            request.setTextContent("Hello World");
            request.setBackgroundColor("#FF5733");

            mockMvc.perform(post("/api/stories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("TEXT"))
                .andExpect(jsonPath("$.textContent").value("Hello World"))
                .andExpect(jsonPath("$.backgroundColor").value("#FF5733"));
        }

        @Test
        @DisplayName("POST / returns 400 when IMAGE has no mediaId")
        void createStory_imageWithoutMediaId_returns400() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);

            PostStoryRequest request = new PostStoryRequest();
            request.setMediaType(com.socialwallet.stories.model.MediaType.IMAGE);

            mockMvc.perform(post("/api/stories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Story Retrieval Tests")
    class StoryRetrieval {

        @Test
        @DisplayName("GET /me returns my active stories")
        void getMyStories_returnsActiveStories() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(authenticatedUserId);
            createAndSaveStory(authenticatedUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());
            createAndSaveStory(authenticatedUserId, com.socialwallet.stories.model.MediaType.TEXT, null);

            mockMvc.perform(get("/api/stories/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("GET /contacts returns stories from mutual contacts")
        void getContactsStories_returnsMutualContactStories() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            createAndSaveSettings(contactUserId);
            createMutualContact(authenticatedUserId, contactUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(get("/api/stories/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].authorId").value(contactUserId.toString()));
        }

        @Test
        @DisplayName("GET /{storyId} returns story when authorized")
        void getStory_authorized_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            createMutualContact(authenticatedUserId, contactUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            Story story = createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(get("/api/stories/" + story.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.storyId").value(story.getId().toString()));
        }

        @Test
        @DisplayName("GET /{storyId} returns 400 when not authorized")
        void getStory_notAuthorized_returns400() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            Story story = createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(get("/api/stories/" + story.getId()))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Story View Tests")
    class StoryViewing {

        @Test
        @DisplayName("POST /{storyId}/view marks story as viewed")
        void markAsViewed_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            createMutualContact(authenticatedUserId, contactUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            Story story = createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(post("/api/stories/" + story.getId() + "/view"))
                .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/stories/" + story.getId()))
                .andExpect(jsonPath("$.viewedByMe").value(true));
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Story Deletion Tests")
    class StoryDeletion {

        @Test
        @DisplayName("DELETE /{storyId} deletes own story")
        void deleteStory_ownStory_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(authenticatedUserId);
            Story story = createAndSaveStory(authenticatedUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(delete("/api/stories/" + story.getId()))
                .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/stories/me"))
                .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("DELETE /{storyId} returns 400 when deleting someone else's story")
        void deleteStory_notOwn_returns400() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            Story story = createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(delete("/api/stories/" + story.getId()))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @WithMockUser(username = "11111111-1111-1111-1111-111111111111")
    @DisplayName("Story Viewers Tests")
    class StoryViewers {

        @Test
        @DisplayName("GET /{storyId}/viewers returns viewers for own story")
        void getViewers_ownStory_succeeds() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(authenticatedUserId);
            Story story = createAndSaveStory(authenticatedUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(get("/api/stories/" + story.getId() + "/viewers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("GET /{storyId}/viewers returns 400 for someone else's story")
        void getViewers_notOwnStory_returns400() throws Exception {
            createAndSaveProfile(authenticatedUserId, "Me");
            createAndSaveProfile(contactUserId, "Contact");
            createAndSaveSettings(authenticatedUserId);
            MediaAsset media = createAndSaveMedia(contactUserId);
            Story story = createAndSaveStory(contactUserId, com.socialwallet.stories.model.MediaType.IMAGE, media.getId());

            mockMvc.perform(get("/api/stories/" + story.getId() + "/viewers"))
                .andExpect(status().isBadRequest());
        }
    }
}